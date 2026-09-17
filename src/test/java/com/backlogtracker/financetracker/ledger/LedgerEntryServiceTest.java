package com.backlogtracker.financetracker.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

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
import com.backlogtracker.financetracker.ledger.domain.LedgerEntryType;
import com.backlogtracker.financetracker.ledger.domain.SplitPartyType;
import com.backlogtracker.financetracker.ledger.dto.CreateLedgerEntryRequest;
import com.backlogtracker.financetracker.ledger.dto.CreateLedgerEntryRequest.ShareInput;
import com.backlogtracker.financetracker.ledger.dto.CreateSettlementRequest;
import com.backlogtracker.financetracker.ledger.dto.LedgerEntryView;
import com.backlogtracker.financetracker.ledger.repository.LedgerEntryRepository;
import com.backlogtracker.financetracker.ledger.repository.SettlementRepository;
import com.backlogtracker.financetracker.ledger.service.LedgerEntryService;

@SpringBootTest
class LedgerEntryServiceTest {

    @Autowired GroupService groupService;
    @Autowired LedgerEntryService ledgerEntryService;
    @Autowired LedgerEntryRepository entries;
    @Autowired SettlementRepository settlements;
    @Autowired GroupRepository groups;
    @Autowired UserRepository users;

    private String payerId;
    private String personAId;
    private String personBId;
    private Group financeGroup;

    @BeforeEach
    void setUp() {
        payerId = users.save(User.builder().name("Payer").email("ledger-payer@x.test")
                .passwordHash("x").role(Role.USER).status(AccountStatus.ACTIVE).handle("ledgerpayer").build()).getId();
        personAId = users.save(User.builder().name("Person A").email("ledger-a@x.test")
                .passwordHash("x").role(Role.USER).status(AccountStatus.ACTIVE).handle("ledgera").build()).getId();
        personBId = users.save(User.builder().name("Person B").email("ledger-b@x.test")
                .passwordHash("x").role(Role.USER).status(AccountStatus.ACTIVE).handle("ledgerb").build()).getId();

        financeGroup = groupService.create("Ledger Test Finance", payerId, Group.APPLET_FINANCE_TRACKER);
        financeGroup = groupService.addMember(financeGroup.getId(), personAId);
        financeGroup = groupService.addMember(financeGroup.getId(), personBId);
    }

    @AfterEach
    void cleanUp() {
        entries.findByGroupIdOrderByCreatedAtDesc(financeGroup.getId()).forEach(e -> entries.deleteById(e.getId()));
        settlements.findByGroupId(financeGroup.getId()).forEach(s -> settlements.deleteById(s.getId()));
        groups.deleteById(financeGroup.getId());
        users.deleteById(payerId);
        users.deleteById(personAId);
        users.deleteById(personBId);
    }

    @Test
    void logsAPersonalExpenseSplitAcrossThreePeopleIncludingThePayer() {
        var request = new CreateLedgerEntryRequest(
                LedgerEntryType.EXPENSE, "Team lunch", new BigDecimal("900.00"), payerId,
                List.of(
                        new ShareInput(SplitPartyType.PERSON, payerId, new BigDecimal("0.34")),
                        new ShareInput(SplitPartyType.PERSON, personAId, new BigDecimal("0.33")),
                        new ShareInput(SplitPartyType.PERSON, personBId, new BigDecimal("0.33"))));

        LedgerEntryView created = ledgerEntryService.create(financeGroup.getId(), payerId, request);

        assertThat(created.id()).isNotBlank();
        assertThat(created.shares()).hasSize(3);
        assertThat(created.payerId()).isEqualTo(payerId);
        assertThat(ledgerEntryService.list(financeGroup.getId(), payerId)).hasSize(1);
    }

    @Test
    void logsAFullyBusinessAttributedExpenseAsAReimbursement() {
        // The confirmed design: a reimbursement is just an expense whose only share is
        // BUSINESS at 100% - no separate "log a reimbursement" entity or code path.
        var request = new CreateLedgerEntryRequest(
                LedgerEntryType.EXPENSE, "Shipment paid on personal card", new BigDecimal("450.00"), payerId,
                List.of(new ShareInput(SplitPartyType.BUSINESS, null, BigDecimal.ONE)));

        LedgerEntryView created = ledgerEntryService.create(financeGroup.getId(), payerId, request);

        assertThat(created.shares()).hasSize(1);
        assertThat(created.shares().get(0).partyType()).isEqualTo(SplitPartyType.BUSINESS);
        assertThat(created.shares().get(0).personId()).isNull();
    }

    @Test
    void updatingAnEntryChangesItsAmountAndSplit() {
        LedgerEntryView created = ledgerEntryService.create(financeGroup.getId(), payerId, new CreateLedgerEntryRequest(
                LedgerEntryType.EXPENSE, "Typo'd yarn order", new BigDecimal("100.00"), payerId,
                List.of(new ShareInput(SplitPartyType.BUSINESS, null, BigDecimal.ONE))));

        LedgerEntryView updated = ledgerEntryService.update(financeGroup.getId(), payerId, created.id(),
                new CreateLedgerEntryRequest(LedgerEntryType.EXPENSE, "Yarn order (corrected)",
                        new BigDecimal("1000.00"), payerId,
                        List.of(new ShareInput(SplitPartyType.BUSINESS, null, BigDecimal.ONE))));

        assertThat(updated.id()).isEqualTo(created.id());
        assertThat(updated.description()).isEqualTo("Yarn order (corrected)");
        assertThat(updated.amount()).isEqualByComparingTo("1000.00");
        assertThat(ledgerEntryService.list(financeGroup.getId(), payerId)).hasSize(1);
    }

    @Test
    void updatingRejectsInvalidSharesJustLikeCreating() {
        LedgerEntryView created = ledgerEntryService.create(financeGroup.getId(), payerId, new CreateLedgerEntryRequest(
                LedgerEntryType.EXPENSE, "Original", new BigDecimal("100.00"), payerId,
                List.of(new ShareInput(SplitPartyType.BUSINESS, null, BigDecimal.ONE))));

        assertThatThrownBy(() -> ledgerEntryService.update(financeGroup.getId(), payerId, created.id(),
                new CreateLedgerEntryRequest(LedgerEntryType.EXPENSE, "Bad split", new BigDecimal("100.00"), payerId,
                        List.of(new ShareInput(SplitPartyType.PERSON, payerId, new BigDecimal("0.5"))))))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("add up to 1");
    }

    @Test
    void updatingAnEntryFromAnotherGroupIsRejected() {
        LedgerEntryView created = ledgerEntryService.create(financeGroup.getId(), payerId, new CreateLedgerEntryRequest(
                LedgerEntryType.EXPENSE, "Original", new BigDecimal("100.00"), payerId,
                List.of(new ShareInput(SplitPartyType.BUSINESS, null, BigDecimal.ONE))));
        Group otherGroup = groupService.create("Other Finance Group", payerId, Group.APPLET_FINANCE_TRACKER);

        try {
            assertThatThrownBy(() -> ledgerEntryService.update(otherGroup.getId(), payerId, created.id(),
                    new CreateLedgerEntryRequest(LedgerEntryType.EXPENSE, "Hijack", new BigDecimal("1.00"), payerId,
                            List.of(new ShareInput(SplitPartyType.BUSINESS, null, BigDecimal.ONE)))))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("not found");
        } finally {
            groups.deleteById(otherGroup.getId());
        }
    }

    @Test
    void rejectsSharesThatDontSumToOne() {
        var request = new CreateLedgerEntryRequest(
                LedgerEntryType.EXPENSE, "Bad split", new BigDecimal("100.00"), payerId,
                List.of(
                        new ShareInput(SplitPartyType.PERSON, payerId, new BigDecimal("0.5")),
                        new ShareInput(SplitPartyType.PERSON, personAId, new BigDecimal("0.2"))));

        assertThatThrownBy(() -> ledgerEntryService.create(financeGroup.getId(), payerId, request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("add up to 1");
    }

    @Test
    void rejectsASharePersonIdThatIsNotAGroupMember() {
        var request = new CreateLedgerEntryRequest(
                LedgerEntryType.EXPENSE, "Outsider split", new BigDecimal("100.00"), payerId,
                List.of(
                        new ShareInput(SplitPartyType.PERSON, payerId, new BigDecimal("0.5")),
                        new ShareInput(SplitPartyType.PERSON, "not-a-member", new BigDecimal("0.5"))));

        assertThatThrownBy(() -> ledgerEntryService.create(financeGroup.getId(), payerId, request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("member");
    }

    @Test
    void rejectsTheSamePersonAppearingTwice() {
        var request = new CreateLedgerEntryRequest(
                LedgerEntryType.EXPENSE, "Double-counted", new BigDecimal("100.00"), payerId,
                List.of(
                        new ShareInput(SplitPartyType.PERSON, payerId, new BigDecimal("0.5")),
                        new ShareInput(SplitPartyType.PERSON, payerId, new BigDecimal("0.5"))));

        assertThatThrownBy(() -> ledgerEntryService.create(financeGroup.getId(), payerId, request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("more than one share");
    }

    @Test
    void rejectsAPayerWhoIsNotAGroupMember() {
        var request = new CreateLedgerEntryRequest(
                LedgerEntryType.EXPENSE, "Bad payer", new BigDecimal("100.00"), "not-a-member",
                List.of(new ShareInput(SplitPartyType.BUSINESS, null, BigDecimal.ONE)));

        assertThatThrownBy(() -> ledgerEntryService.create(financeGroup.getId(), payerId, request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("payerId");
    }

    @Test
    void balancesNetOutAPersonalSplitBetweenPayerAndOthers() {
        // Payer fronts 900 for lunch, split evenly three ways including themselves - A and
        // B should each owe the payer a third; the payer's own third is absorbed, not owed.
        ledgerEntryService.create(financeGroup.getId(), payerId, new CreateLedgerEntryRequest(
                LedgerEntryType.EXPENSE, "Lunch", new BigDecimal("900.00"), payerId,
                List.of(
                        new ShareInput(SplitPartyType.PERSON, payerId, new BigDecimal("0.3334")),
                        new ShareInput(SplitPartyType.PERSON, personAId, new BigDecimal("0.3333")),
                        new ShareInput(SplitPartyType.PERSON, personBId, new BigDecimal("0.3333")))));

        var balances = ledgerEntryService.balances(financeGroup.getId(), payerId);

        var payerBalance = balances.stream().filter(b -> b.personId().equals(payerId)).findFirst().orElseThrow();
        var aBalance = balances.stream().filter(b -> b.personId().equals(personAId)).findFirst().orElseThrow();
        var bBalance = balances.stream().filter(b -> b.personId().equals(personBId)).findFirst().orElseThrow();

        assertThat(payerBalance.netFromOthers()).isEqualByComparingTo("599.94");
        assertThat(aBalance.netFromOthers()).isEqualByComparingTo("-299.97");
        assertThat(bBalance.netFromOthers()).isEqualByComparingTo("-299.97");
        assertThat(payerBalance.owedByBusiness()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void aFullyBusinessAttributedExpenseOwesThePayerNotOtherMembers() {
        ledgerEntryService.create(financeGroup.getId(), payerId, new CreateLedgerEntryRequest(
                LedgerEntryType.EXPENSE, "Shipment", new BigDecimal("450.00"), payerId,
                List.of(new ShareInput(SplitPartyType.BUSINESS, null, BigDecimal.ONE))));

        var balances = ledgerEntryService.balances(financeGroup.getId(), payerId);
        var payerBalance = balances.stream().filter(b -> b.personId().equals(payerId)).findFirst().orElseThrow();

        assertThat(payerBalance.owedByBusiness()).isEqualByComparingTo("450.00");
        assertThat(payerBalance.netFromOthers()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void incomeCreditedToAPersonIncreasesWhatTheBusinessOwesThem() {
        ledgerEntryService.create(financeGroup.getId(), payerId, new CreateLedgerEntryRequest(
                LedgerEntryType.INCOME, "Investment", new BigDecimal("5000.00"), personAId,
                List.of(new ShareInput(SplitPartyType.PERSON, personAId, BigDecimal.ONE))));

        var balances = ledgerEntryService.balances(financeGroup.getId(), payerId);
        var aBalance = balances.stream().filter(b -> b.personId().equals(personAId)).findFirst().orElseThrow();

        assertThat(aBalance.owedByBusiness()).isEqualByComparingTo("5000.00");
    }

    @Test
    void incomeCreditedToBusinessCreatesNoPersonalBalance() {
        ledgerEntryService.create(financeGroup.getId(), payerId, new CreateLedgerEntryRequest(
                LedgerEntryType.INCOME, "Order payment", new BigDecimal("1500.00"), payerId,
                List.of(new ShareInput(SplitPartyType.BUSINESS, null, BigDecimal.ONE))));

        var balances = ledgerEntryService.balances(financeGroup.getId(), payerId);

        assertThat(balances).isEmpty();
    }

    @Test
    void settlingUpReducesOnlyTheSpecificDebtNotAThirdPartysBalance() {
        // Payer fronts 900, split three ways evenly (300 each). A settles their 300 with
        // the payer - only A's and the payer's balances should move; B's stays untouched.
        ledgerEntryService.create(financeGroup.getId(), payerId, new CreateLedgerEntryRequest(
                LedgerEntryType.EXPENSE, "Lunch", new BigDecimal("900.00"), payerId,
                List.of(
                        new ShareInput(SplitPartyType.PERSON, payerId, new BigDecimal("0.3334")),
                        new ShareInput(SplitPartyType.PERSON, personAId, new BigDecimal("0.3333")),
                        new ShareInput(SplitPartyType.PERSON, personBId, new BigDecimal("0.3333")))));

        ledgerEntryService.settleUp(financeGroup.getId(), payerId,
                new CreateSettlementRequest(SplitPartyType.PERSON, personAId, new BigDecimal("299.97")));

        var balances = ledgerEntryService.balances(financeGroup.getId(), payerId).stream()
                .collect(java.util.stream.Collectors.toMap(b -> b.personId(), b -> b));

        assertThat(balances.get(personAId).netFromOthers()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(balances.get(personBId).netFromOthers()).isEqualByComparingTo("-299.97");
        assertThat(balances.get(payerId).netFromOthers()).isEqualByComparingTo("299.97");
    }

    @Test
    void settlingUpFromTheBusinessReducesWhatTheBusinessOwes() {
        ledgerEntryService.create(financeGroup.getId(), payerId, new CreateLedgerEntryRequest(
                LedgerEntryType.EXPENSE, "Shipment", new BigDecimal("450.00"), payerId,
                List.of(new ShareInput(SplitPartyType.BUSINESS, null, BigDecimal.ONE))));

        ledgerEntryService.settleUp(financeGroup.getId(), payerId,
                new CreateSettlementRequest(SplitPartyType.BUSINESS, null, new BigDecimal("450.00")));

        // Settling doesn't make the person disappear from the list - they had real
        // activity, now netted to zero, which is more useful to show than silently
        // vanishing (a UI that shows "you're now settled up" beats one that shows nothing).
        var balances = ledgerEntryService.balances(financeGroup.getId(), payerId);
        var payerBalance = balances.stream().filter(b -> b.personId().equals(payerId)).findFirst().orElseThrow();
        assertThat(payerBalance.owedByBusiness()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void onlyThePersonOwedCanSettleUp() {
        // Only the caller (whoever is owed) can create a settlement recording that they
        // received payment - request.toPersonId is always the caller, never a body field,
        // but the amount-owed check on their own balance is what actually enforces this:
        // a bystander with no balance can't record settling someone else's debt.
        ledgerEntryService.create(financeGroup.getId(), payerId, new CreateLedgerEntryRequest(
                LedgerEntryType.EXPENSE, "Lunch", new BigDecimal("300.00"), payerId,
                List.of(
                        new ShareInput(SplitPartyType.PERSON, payerId, new BigDecimal("0.5")),
                        new ShareInput(SplitPartyType.PERSON, personAId, new BigDecimal("0.5")))));

        assertThatThrownBy(() -> ledgerEntryService.settleUp(financeGroup.getId(), personBId,
                new CreateSettlementRequest(SplitPartyType.PERSON, personAId, new BigDecimal("150.00"))))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("more than is currently owed");
    }

    @Test
    void rejectsSettlingMoreThanIsCurrentlyOwed() {
        ledgerEntryService.create(financeGroup.getId(), payerId, new CreateLedgerEntryRequest(
                LedgerEntryType.EXPENSE, "Lunch", new BigDecimal("300.00"), payerId,
                List.of(
                        new ShareInput(SplitPartyType.PERSON, payerId, new BigDecimal("0.5")),
                        new ShareInput(SplitPartyType.PERSON, personAId, new BigDecimal("0.5")))));

        assertThatThrownBy(() -> ledgerEntryService.settleUp(financeGroup.getId(), payerId,
                new CreateSettlementRequest(SplitPartyType.PERSON, personAId, new BigDecimal("999.00"))))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("more than is currently owed");
    }

    /** Regression test for a read-then-write race: settleUp used to recompute balances,
     *  validate, and save with nothing serializing concurrent calls by the same payee —
     *  two racing settlements (e.g. a double-click) could both read the same pre-settlement
     *  balance and both pass the "not more than owed" check, over-settling. It's now
     *  guarded by a per-(group, payee) lock, so N concurrent attempts to settle a fixed
     *  debt one small chunk at a time can never together exceed what was actually owed —
     *  exactly enough of them succeed to cover the debt and the rest are correctly
     *  rejected, never all N succeeding. */
    @Test
    void concurrentSettlementsBySamePayeeNeverExceedWhatWasActuallyOwed() throws Exception {
        ledgerEntryService.create(financeGroup.getId(), payerId, new CreateLedgerEntryRequest(
                LedgerEntryType.EXPENSE, "Lunch", new BigDecimal("100.00"), payerId,
                List.of(
                        new ShareInput(SplitPartyType.PERSON, payerId, new BigDecimal("0.5")),
                        new ShareInput(SplitPartyType.PERSON, personAId, new BigDecimal("0.5")))));
        // personA owes payer exactly 50.00. Fire 20 concurrent 5.00 settlement attempts —
        // if the race exists, more than 10 of them could succeed and personA's recorded
        // debt would be settled more than once over.
        int n = 20;
        BigDecimal chunk = new BigDecimal("5.00");
        ExecutorService pool = Executors.newFixedThreadPool(10);
        try {
            List<Callable<Boolean>> tasks = IntStream.range(0, n)
                    .<Callable<Boolean>>mapToObj(i -> () -> {
                        try {
                            ledgerEntryService.settleUp(financeGroup.getId(), payerId,
                                    new CreateSettlementRequest(SplitPartyType.PERSON, personAId, chunk));
                            return true;
                        } catch (ResponseStatusException e) {
                            return false;
                        }
                    })
                    .toList();
            long succeeded = pool.invokeAll(tasks).stream().map(LedgerEntryServiceTest::get).filter(Boolean::booleanValue).count();
            assertThat(succeeded).isEqualTo(10);
        } finally {
            pool.shutdownNow();
        }

        var balances = ledgerEntryService.balances(financeGroup.getId(), payerId).stream()
                .collect(java.util.stream.Collectors.toMap(b -> b.personId(), b -> b));
        assertThat(balances.get(personAId).netFromOthers()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    private static <T> T get(java.util.concurrent.Future<T> f) {
        try {
            return f.get();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
