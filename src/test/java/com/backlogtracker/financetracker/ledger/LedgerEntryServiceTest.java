package com.backlogtracker.financetracker.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.server.ResponseStatusException;

import com.backlogtracker.commons.group.domain.Group;
import com.backlogtracker.commons.group.repository.GroupRepository;
import com.backlogtracker.commons.group.service.GroupService;
import com.backlogtracker.commons.user.domain.AccountStatus;
import com.backlogtracker.commons.user.domain.Role;
import com.backlogtracker.commons.user.domain.User;
import com.backlogtracker.commons.user.repository.UserRepository;
import com.backlogtracker.financetracker.ledger.domain.PartyType;
import com.backlogtracker.financetracker.ledger.dto.CreateLedgerEntryRequest;
import com.backlogtracker.financetracker.ledger.dto.CreateLedgerEntryRequest.PartyInput;
import com.backlogtracker.financetracker.ledger.dto.LedgerEntryView;
import com.backlogtracker.financetracker.ledger.repository.LedgerEntryRepository;
import com.backlogtracker.financetracker.ledger.service.LedgerEntryService;

/** ad-1: the ledger is now a real double-entry table — always exactly one Debit and one
 *  Credit party per row, never a multi-way split (replaces the earlier shares/Settlement
 *  model entirely). */
@SpringBootTest
class LedgerEntryServiceTest {

    @Autowired GroupService groupService;
    @Autowired LedgerEntryService ledgerEntryService;
    @Autowired LedgerEntryRepository entries;
    @Autowired GroupRepository groups;
    @Autowired UserRepository users;

    private String aliceId;
    private String bobId;
    private Group financeGroup;

    @BeforeEach
    void setUp() {
        aliceId = users.save(User.builder().name("Alice").email("ledger-alice@x.test")
                .passwordHash("x").role(Role.USER).status(AccountStatus.ACTIVE).handle("ledgeralice").build()).getId();
        bobId = users.save(User.builder().name("Bob").email("ledger-bob@x.test")
                .passwordHash("x").role(Role.USER).status(AccountStatus.ACTIVE).handle("ledgerbob").build()).getId();

        financeGroup = groupService.create("Ledger Test Finance", aliceId, Group.APPLET_FINANCE_TRACKER);
        financeGroup = groupService.addMember(financeGroup.getId(), bobId);
    }

    @AfterEach
    void cleanUp() {
        entries.findByGroupIdOrderByDateDesc(financeGroup.getId()).forEach(e -> entries.deleteById(e.getId()));
        groups.deleteById(financeGroup.getId());
        users.deleteById(aliceId);
        users.deleteById(bobId);
    }

    private static PartyInput member(String userId) {
        return new PartyInput(PartyType.MEMBER, userId, null);
    }

    private static PartyInput business() {
        return new PartyInput(PartyType.BUSINESS, null, null);
    }

    private static PartyInput customer(String name) {
        return new PartyInput(PartyType.CUSTOMER, null, name);
    }

    private static PartyInput external(String name) {
        return new PartyInput(PartyType.EXTERNAL, null, name);
    }

    @Test
    void createsARealDebitCreditEntry() {
        var request = new CreateLedgerEntryRequest(null, "Yarn restock", new BigDecimal("2400.00"),
                business(), member(aliceId));

        LedgerEntryView created = ledgerEntryService.create(financeGroup.getId(), aliceId, request);

        assertThat(created.id()).isNotBlank();
        assertThat(created.debit().type()).isEqualTo(PartyType.BUSINESS);
        assertThat(created.credit().type()).isEqualTo(PartyType.MEMBER);
        assertThat(created.credit().userId()).isEqualTo(aliceId);
        assertThat(ledgerEntryService.list(financeGroup.getId(), aliceId)).hasSize(1);
    }

    @Test
    void aMemberToMemberEntryIsARealPersonToPersonTransaction() {
        // No more shared-split construct: Bob paying Alice back is its own real entry.
        var request = new CreateLedgerEntryRequest(null, "Bob repaid Alice for shared supplies",
                new BigDecimal("300.00"), member(bobId), member(aliceId));

        LedgerEntryView created = ledgerEntryService.create(financeGroup.getId(), bobId, request);

        assertThat(created.debit().userId()).isEqualTo(bobId);
        assertThat(created.credit().userId()).isEqualTo(aliceId);
    }

    @Test
    void deletingAnEntryRemovesItFromTheLedger() {
        LedgerEntryView created = ledgerEntryService.create(financeGroup.getId(), aliceId,
                new CreateLedgerEntryRequest(null, "Typo'd entry", new BigDecimal("100.00"), business(), member(aliceId)));

        ledgerEntryService.delete(financeGroup.getId(), aliceId, created.id());

        assertThat(ledgerEntryService.list(financeGroup.getId(), aliceId)).isEmpty();
    }

    @Test
    void deletingAnEntryFromAnotherGroupIsRejected() {
        LedgerEntryView created = ledgerEntryService.create(financeGroup.getId(), aliceId,
                new CreateLedgerEntryRequest(null, "Original", new BigDecimal("100.00"), business(), member(aliceId)));
        Group otherGroup = groupService.create("Other Finance Group", aliceId, Group.APPLET_FINANCE_TRACKER);

        try {
            assertThatThrownBy(() -> ledgerEntryService.delete(otherGroup.getId(), aliceId, created.id()))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("not found");
        } finally {
            groups.deleteById(otherGroup.getId());
        }
    }

    @Test
    void rejectsAMemberPartyThatIsNotAGroupMember() {
        var request = new CreateLedgerEntryRequest(null, "Outsider", new BigDecimal("100.00"),
                business(), member("not-a-member"));

        assertThatThrownBy(() -> ledgerEntryService.create(financeGroup.getId(), aliceId, request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("member");
    }

    @Test
    void rejectsACustomerOrExternalPartyWithNoDisplayName() {
        var request = new CreateLedgerEntryRequest(null, "Missing name", new BigDecimal("100.00"),
                customer(""), member(aliceId));

        assertThatThrownBy(() -> ledgerEntryService.create(financeGroup.getId(), aliceId, request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("displayName");
    }

    @Test
    void balancesAreLiveCreditsMinusDebitsPerMember() {
        // Business owes Alice 2400 (she fronted it): Debit BUSINESS, Credit Alice.
        ledgerEntryService.create(financeGroup.getId(), aliceId,
                new CreateLedgerEntryRequest(null, "Yarn restock", new BigDecimal("2400.00"), business(), member(aliceId)));
        // Alice pays Bob back 300: Debit Alice, Credit Bob.
        ledgerEntryService.create(financeGroup.getId(), aliceId,
                new CreateLedgerEntryRequest(null, "Repay Bob", new BigDecimal("300.00"), member(aliceId), member(bobId)));

        var balances = ledgerEntryService.balances(financeGroup.getId(), aliceId).stream()
                .collect(java.util.stream.Collectors.toMap(b -> b.userId(), b -> b));

        assertThat(balances.get(aliceId).net()).isEqualByComparingTo("2100.00"); // +2400 credit, -300 debit
        assertThat(balances.get(bobId).net()).isEqualByComparingTo("300.00");
    }

    @Test
    void businessAndCustomerPartiesNeverEnterTheMemberBalance() {
        // Debit CUSTOMER, Credit BUSINESS — a plain sale, no member involved at all.
        ledgerEntryService.create(financeGroup.getId(), aliceId,
                new CreateLedgerEntryRequest(null, "Order payment", new BigDecimal("1500.00"),
                        customer("Priya Sharma"), business()));

        assertThat(ledgerEntryService.balances(financeGroup.getId(), aliceId)).isEmpty();
    }

    @Test
    void externalSuggestionsAreSortedByHowOftenEachNameWasUsed() {
        ledgerEntryService.create(financeGroup.getId(), aliceId,
                new CreateLedgerEntryRequest(null, "Yarn supplier A", new BigDecimal("100.00"), business(), external("Acme Yarns")));
        ledgerEntryService.create(financeGroup.getId(), aliceId,
                new CreateLedgerEntryRequest(null, "Yarn supplier A again", new BigDecimal("50.00"), business(), external("Acme Yarns")));
        ledgerEntryService.create(financeGroup.getId(), aliceId,
                new CreateLedgerEntryRequest(null, "One-off supplier", new BigDecimal("20.00"), business(), external("Rare Threads")));

        var suggestions = ledgerEntryService.externalSuggestions(financeGroup.getId(), aliceId);

        assertThat(suggestions).hasSize(2);
        assertThat(suggestions.get(0).displayName()).isEqualTo("Acme Yarns");
        assertThat(suggestions.get(0).useCount()).isEqualTo(2);
        assertThat(suggestions.get(1).displayName()).isEqualTo("Rare Threads");
    }
}
