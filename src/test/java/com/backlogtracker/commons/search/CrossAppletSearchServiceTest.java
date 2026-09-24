package com.backlogtracker.commons.search;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.backlogtracker.commons.group.domain.Group;
import com.backlogtracker.commons.group.repository.GroupRepository;
import com.backlogtracker.commons.group.service.GroupService;
import com.backlogtracker.commons.link.service.GroupLinkService;
import com.backlogtracker.commons.user.domain.AccountStatus;
import com.backlogtracker.commons.user.domain.Role;
import com.backlogtracker.commons.user.domain.User;
import com.backlogtracker.commons.user.repository.UserRepository;
import com.backlogtracker.financetracker.ledger.dto.CreateLedgerEntryRequest;
import com.backlogtracker.financetracker.ledger.dto.CreateLedgerEntryRequest.PartyInput;
import com.backlogtracker.financetracker.ledger.domain.PartyType;
import com.backlogtracker.financetracker.ledger.repository.LedgerEntryRepository;
import com.backlogtracker.financetracker.ledger.service.LedgerEntryService;
import com.backlogtracker.ordertracker.customer.domain.AcquisitionChannel;
import com.backlogtracker.ordertracker.customer.dto.CustomerView;
import com.backlogtracker.ordertracker.customer.dto.UpsertCustomerRequest;
import com.backlogtracker.ordertracker.customer.repository.CustomerRepository;
import com.backlogtracker.ordertracker.customer.service.CustomerService;
import com.backlogtracker.ordertracker.master.domain.Creator;
import com.backlogtracker.ordertracker.master.repository.CreatorRepository;
import com.backlogtracker.ordertracker.master.service.CreatorService;
import com.backlogtracker.ordertracker.order.dto.CreateOrderRequest;
import com.backlogtracker.ordertracker.order.dto.OrderView;
import com.backlogtracker.ordertracker.order.repository.OrderRepository;
import com.backlogtracker.ordertracker.order.service.OrderService;

/** ad-5: end-to-end coverage across the real orchestrator + all 4 applet providers — a
 *  business's own orders/customers, plus (once linked) a Finance Tracker group's ledger
 *  entries. */
@SpringBootTest
class CrossAppletSearchServiceTest {

    @Autowired CrossAppletSearchService searchService;
    @Autowired GroupService groupService;
    @Autowired GroupLinkService groupLinkService;
    @Autowired GroupRepository groups;
    @Autowired UserRepository users;
    @Autowired CreatorService creatorService;
    @Autowired CreatorRepository creators;
    @Autowired CustomerService customerService;
    @Autowired CustomerRepository customers;
    @Autowired OrderService orderService;
    @Autowired OrderRepository orders;
    @Autowired LedgerEntryService ledgerEntryService;
    @Autowired LedgerEntryRepository ledgerEntries;

    private String userId;
    private String outsiderId;
    private Group business;

    @BeforeEach
    void setUp() {
        userId = users.save(User.builder().name("Search Tester").email("search-tester@x.test")
                .passwordHash("x").role(Role.USER).status(AccountStatus.ACTIVE).handle("searchtester").build()).getId();
        outsiderId = users.save(User.builder().name("Search Outsider").email("search-outsider@x.test")
                .passwordHash("x").role(Role.USER).status(AccountStatus.ACTIVE).handle("searchoutsider").build()).getId();
        business = groupService.create("Search Business", userId, Group.APPLET_ORDER_TRACKER);
    }

    @AfterEach
    void cleanUp() {
        orders.findByGroupId(business.getId()).forEach(o -> orders.deleteById(o.getId()));
        customers.findByGroupId(business.getId()).forEach(c -> customers.deleteById(c.getId()));
        creators.findByGroupId(business.getId()).forEach(c -> creators.deleteById(c.getId()));
        groups.findByMemberIdsContaining(userId).forEach(g -> {
            if (g.getAppletKey().equals(Group.APPLET_FINANCE_TRACKER)) {
                ledgerEntries.findByGroupIdOrderByDateDesc(g.getId()).forEach(e -> ledgerEntries.deleteById(e.getId()));
            }
            groups.delete(g);
        });
        users.deleteById(userId);
        users.deleteById(outsiderId);
    }

    private OrderView seedOrder(String itemName) {
        Creator creator = creatorService.upsertMyProfile(business.getId(), userId, null, "Bengaluru", 4);
        CustomerView customer = customerService.create(business.getId(), userId,
                new UpsertCustomerRequest("Priya Sharma", null, null, null, AcquisitionChannel.INSTAGRAM,
                        null, null, null));
        return orderService.create(business.getId(), userId,
                new CreateOrderRequest(customer.id(), "INDIVIDUAL", creator.getId(),
                        itemName, null, null, null, null, null, 0, null, null, null,
                        null, null, null, null, null, 0, 0, null, null, 0));
    }

    @Test
    void findsAnOrderByItemNameSubstringCaseInsensitively() {
        OrderView order = seedOrder("Sunset Coral Tote Bag");

        List<SearchResult> results = searchService.search(business.getId(), userId, "coral tote");

        assertThat(results).anySatisfy(r -> {
            assertThat(r.category()).isEqualTo("Order");
            assertThat(r.id()).isEqualTo(order.id());
            assertThat(r.path()).isEqualTo("/ordertracker/orders/" + order.id());
        });
    }

    @Test
    void findsAnOrderByOrderNumberSubstring() {
        OrderView order = seedOrder("Something Else");

        List<SearchResult> results = searchService.search(business.getId(), userId,
                order.orderNumber().substring(0, 5));

        assertThat(results).anySatisfy(r -> assertThat(r.id()).isEqualTo(order.id()));
    }

    @Test
    void findsACustomerByDecryptedNameSubstring() {
        seedOrder("irrelevant"); // seeds "Priya Sharma" as a side effect

        List<SearchResult> results = searchService.search(business.getId(), userId, "priya");

        assertThat(results).anySatisfy(r -> {
            assertThat(r.category()).isEqualTo("Customer");
            assertThat(r.title()).isEqualTo("Priya Sharma");
        });
    }

    @Test
    void queryShorterThanTwoCharactersReturnsNothing() {
        seedOrder("Sunset Coral Tote Bag");

        assertThat(searchService.search(business.getId(), userId, "s")).isEmpty();
    }

    @Test
    void aNonMemberCannotSearchTheGroup() {
        seedOrder("Sunset Coral Tote Bag");

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> searchService.search(business.getId(), outsiderId, "sunset"))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }

    @Test
    void withNoLinkedFinanceGroupOnlyTheOwnAppletIsSearched() {
        seedOrder("irrelevant");

        List<SearchResult> results = searchService.search(business.getId(), userId, "reimbursement");

        assertThat(results).isEmpty();
    }

    @Test
    void aLinkedFinanceGroupsLedgerEntriesAreSearchedToo() {
        seedOrder("irrelevant");
        Group finance = groupService.create("Search Finance", userId, Group.APPLET_FINANCE_TRACKER);
        groupLinkService.link(business.getId(), userId, finance.getId());

        ledgerEntryService.create(finance.getId(), userId, new CreateLedgerEntryRequest(
                null, "Yarn reimbursement for order #1", new BigDecimal("450.00"),
                new PartyInput(PartyType.BUSINESS, null, null), new PartyInput(PartyType.MEMBER, userId, null)));

        List<SearchResult> results = searchService.search(business.getId(), userId, "reimbursement");

        assertThat(results).anySatisfy(r -> {
            assertThat(r.category()).isEqualTo("Ledger entry");
            assertThat(r.title()).isEqualTo("Yarn reimbursement for order #1");
            assertThat(r.path()).isEqualTo("/financetracker/ledger");
        });

        // cleanup for this test's extra finance group (not created via a Creator/Customer
        // needing the shared AfterEach's business-scoped cleanup)
        ledgerEntries.findByGroupIdOrderByDateDesc(finance.getId()).forEach(e -> ledgerEntries.deleteById(e.getId()));
        groups.deleteById(finance.getId());
    }

    @Test
    void searchingFromTheFinanceSideAlsoFindsTheLinkedBusinessesOrders() {
        OrderView order = seedOrder("Sunset Coral Tote Bag");
        Group finance = groupService.create("Search Finance 2", userId, Group.APPLET_FINANCE_TRACKER);
        groupLinkService.link(business.getId(), userId, finance.getId());

        List<SearchResult> results = searchService.search(finance.getId(), userId, "coral tote");

        assertThat(results).anySatisfy(r -> assertThat(r.id()).isEqualTo(order.id()));

        groups.deleteById(finance.getId());
    }
}
