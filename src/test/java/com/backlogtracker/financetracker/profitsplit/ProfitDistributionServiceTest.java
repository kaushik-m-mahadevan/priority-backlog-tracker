package com.backlogtracker.financetracker.profitsplit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.server.ResponseStatusException;

import com.backlogtracker.commons.approval.domain.ApprovalStatus;
import com.backlogtracker.commons.approval.repository.ApprovalRequestRepository;
import com.backlogtracker.commons.group.domain.Group;
import com.backlogtracker.commons.group.repository.GroupRepository;
import com.backlogtracker.commons.group.service.GroupService;
import com.backlogtracker.commons.notification.domain.NotificationType;
import com.backlogtracker.commons.notification.repository.NotificationRepository;
import com.backlogtracker.commons.user.domain.AccountStatus;
import com.backlogtracker.commons.user.domain.Role;
import com.backlogtracker.commons.user.domain.User;
import com.backlogtracker.commons.user.repository.UserRepository;
import com.backlogtracker.financetracker.ledger.domain.LedgerEntryType;
import com.backlogtracker.financetracker.ledger.repository.LedgerEntryRepository;
import com.backlogtracker.financetracker.ledger.service.LedgerEntryService;
import com.backlogtracker.financetracker.profitsplit.dto.ProfitDistributionView;
import com.backlogtracker.financetracker.profitsplit.dto.ProposeProfitDistributionRequest;
import com.backlogtracker.financetracker.profitsplit.dto.ProposeProfitDistributionRequest.RecipientInput;
import com.backlogtracker.financetracker.profitsplit.service.ProfitDistributionService;

@SpringBootTest
class ProfitDistributionServiceTest {

    @Autowired GroupService groupService;
    @Autowired ProfitDistributionService profitDistributionService;
    @Autowired LedgerEntryService ledgerEntryService;
    @Autowired LedgerEntryRepository entries;
    @Autowired ApprovalRequestRepository approvalRequests;
    @Autowired GroupRepository groups;
    @Autowired UserRepository users;
    @Autowired NotificationRepository notifications;

    private String coordinatorId;
    private String creatorAId;
    private String creatorBId;
    private Group financeGroup;

    @BeforeEach
    void setUp() {
        coordinatorId = users.save(User.builder().name("Coordinator").email("profitsplit-coord@x.test")
                .passwordHash("x").role(Role.USER).status(AccountStatus.ACTIVE).handle("profitsplitcoord").build()).getId();
        creatorAId = users.save(User.builder().name("Creator A").email("profitsplit-a@x.test")
                .passwordHash("x").role(Role.USER).status(AccountStatus.ACTIVE).handle("profitsplita").build()).getId();
        creatorBId = users.save(User.builder().name("Creator B").email("profitsplit-b@x.test")
                .passwordHash("x").role(Role.USER).status(AccountStatus.ACTIVE).handle("profitsplitb").build()).getId();

        financeGroup = groupService.create("Profit Split Test Finance", coordinatorId, Group.APPLET_FINANCE_TRACKER);
        financeGroup = groupService.addMember(financeGroup.getId(), creatorAId);
        financeGroup = groupService.addMember(financeGroup.getId(), creatorBId);
    }

    @AfterEach
    void cleanUp() {
        entries.findByGroupIdOrderByCreatedAtDesc(financeGroup.getId()).forEach(e -> entries.deleteById(e.getId()));
        approvalRequests.findByGroupId(financeGroup.getId()).forEach(r -> approvalRequests.deleteById(r.getId()));
        groups.deleteById(financeGroup.getId());
        users.deleteById(coordinatorId);
        users.deleteById(creatorAId);
        users.deleteById(creatorBId);
    }

    private ProposeProfitDistributionRequest proportionalRequest() {
        return new ProposeProfitDistributionRequest(List.of("Order #123"), new BigDecimal("300.00"), List.of(
                new RecipientInput(creatorAId, 2, null),
                new RecipientInput(creatorBId, 1, null)));
    }

    @Test
    void splitsProportionallyByUnitsCompletedAndCreatesIncomeEntriesOnceUnanimouslyApproved() {
        ProfitDistributionView proposed = profitDistributionService.propose(financeGroup.getId(), coordinatorId, proportionalRequest());
        assertThat(proposed.status()).isEqualTo(ApprovalStatus.PENDING);

        profitDistributionService.approve(financeGroup.getId(), creatorAId, proposed.requestId());
        ProfitDistributionView resolved = profitDistributionService.approve(financeGroup.getId(), creatorBId, proposed.requestId());

        assertThat(resolved.status()).isEqualTo(ApprovalStatus.APPROVED);
        assertThat(resolved.recipients()).extracting(ProfitDistributionView.RecipientAmountView::personId, ProfitDistributionView.RecipientAmountView::amount)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(creatorAId, new BigDecimal("200.00")),
                        org.assertj.core.groups.Tuple.tuple(creatorBId, new BigDecimal("100.00")));

        var incomeEntries = entries.findByGroupIdOrderByCreatedAtDesc(financeGroup.getId()).stream()
                .filter(e -> e.getType() == LedgerEntryType.INCOME).toList();
        assertThat(incomeEntries).hasSize(2);
        assertThat(incomeEntries).extracting(e -> e.getAmount())
                .containsExactlyInAnyOrder(new BigDecimal("200.00"), new BigDecimal("100.00"));
    }

    /** Regression test for the documented remainder-absorption rule in computeAmounts():
     *  the last proportional recipient gets `remaining.subtract(allocated)` instead of its
     *  own division, specifically to absorb a non-terminating remainder. The 2:1-on-300
     *  test above is evenly divisible and never exercises that branch — a 1:1:1 split on
     *  100.00 forces a real remainder (100/3 = 33.333...) so a future "simplification" that
     *  makes every recipient divide independently would be caught here (it would produce
     *  33.33 + 33.33 + 33.33 = 99.99, a penny short). */
    @Test
    void anUnevenThreeWaySplitAbsorbsTheRoundingRemainderOnTheLastRecipient() {
        ProposeProfitDistributionRequest request = new ProposeProfitDistributionRequest(
                List.of("Order #uneven-split"), new BigDecimal("100.00"), List.of(
                        new RecipientInput(coordinatorId, 1, null),
                        new RecipientInput(creatorAId, 1, null),
                        new RecipientInput(creatorBId, 1, null)));

        ProfitDistributionView proposed = profitDistributionService.propose(financeGroup.getId(), coordinatorId, request);
        profitDistributionService.approve(financeGroup.getId(), creatorAId, proposed.requestId());
        ProfitDistributionView resolved = profitDistributionService.approve(financeGroup.getId(), creatorBId, proposed.requestId());

        assertThat(resolved.recipients()).extracting(ProfitDistributionView.RecipientAmountView::amount)
                .containsExactlyInAnyOrder(new BigDecimal("33.33"), new BigDecimal("33.33"), new BigDecimal("33.34"));
        BigDecimal total = resolved.recipients().stream().map(ProfitDistributionView.RecipientAmountView::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(total).isEqualByComparingTo("100.00");
    }

    @Test
    void aManualOverrideTakesThatRecipientOutOfTheProportionalPool() {
        var request = new ProposeProfitDistributionRequest(List.of("Order #124"), new BigDecimal("300.00"), List.of(
                new RecipientInput(creatorAId, 2, new BigDecimal("50.00")),
                new RecipientInput(creatorBId, 1, null)));

        ProfitDistributionView proposed = profitDistributionService.propose(financeGroup.getId(), coordinatorId, request);
        ProfitDistributionView resolved = profitDistributionService.approve(financeGroup.getId(), creatorAId, proposed.requestId());
        resolved = profitDistributionService.approve(financeGroup.getId(), creatorBId, proposed.requestId());

        assertThat(resolved.recipients()).extracting(ProfitDistributionView.RecipientAmountView::personId, ProfitDistributionView.RecipientAmountView::amount)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(creatorAId, new BigDecimal("50.00")),
                        org.assertj.core.groups.Tuple.tuple(creatorBId, new BigDecimal("250.00")));
    }

    @Test
    void rejectsOverridesThatExceedTheTotalProfit() {
        var request = new ProposeProfitDistributionRequest(List.of("Order #125"), new BigDecimal("100.00"), List.of(
                new RecipientInput(creatorAId, 0, new BigDecimal("80.00")),
                new RecipientInput(creatorBId, 0, new BigDecimal("50.00"))));

        assertThatThrownBy(() -> profitDistributionService.propose(financeGroup.getId(), coordinatorId, request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("more than the total profit");
    }

    /** ad-4: proposing notifies the other members (not the proposer) so they know a split
     *  is waiting on their approval. */
    @Test
    void proposingNotifiesTheOtherMembersButNotTheProposer() {
        profitDistributionService.propose(financeGroup.getId(), coordinatorId, proportionalRequest());

        assertThat(notifications.findByUserIdOrderByCreatedAtDesc(creatorAId))
                .anySatisfy(n -> assertThat(n.getType()).isEqualTo(NotificationType.PROFIT_DISTRIBUTION_PROPOSED));
        assertThat(notifications.findByUserIdOrderByCreatedAtDesc(creatorBId))
                .anySatisfy(n -> assertThat(n.getType()).isEqualTo(NotificationType.PROFIT_DISTRIBUTION_PROPOSED));
        assertThat(notifications.findByUserIdOrderByCreatedAtDesc(coordinatorId))
                .noneMatch(n -> n.getType() == NotificationType.PROFIT_DISTRIBUTION_PROPOSED);
    }

    @Test
    void aSingleRejectionCancelsTheProposalAndCreatesNoLedgerEntries() {
        ProfitDistributionView proposed = profitDistributionService.propose(financeGroup.getId(), coordinatorId, proportionalRequest());

        ProfitDistributionView rejected = profitDistributionService.reject(financeGroup.getId(), creatorAId, proposed.requestId());

        assertThat(rejected.status()).isEqualTo(ApprovalStatus.REJECTED);
        assertThat(entries.findByGroupIdOrderByCreatedAtDesc(financeGroup.getId())).isEmpty();
    }

    @Test
    void aMemberLeavingMidApprovalInvalidatesTheProposal() {
        ProfitDistributionView proposed = profitDistributionService.propose(financeGroup.getId(), coordinatorId, proportionalRequest());

        groupService.leave(financeGroup.getId(), creatorBId);

        ProfitDistributionView reloaded = profitDistributionService.get(financeGroup.getId(), coordinatorId, proposed.requestId());
        assertThat(reloaded.status()).isEqualTo(ApprovalStatus.INVALIDATED);
    }

    @Test
    void onlyOnePendingProposalPerOrderReferenceIsAllowed() {
        profitDistributionService.propose(financeGroup.getId(), coordinatorId, proportionalRequest());

        assertThatThrownBy(() -> profitDistributionService.propose(financeGroup.getId(), coordinatorId, proportionalRequest()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("already pending");
    }

    @Test
    void differentOrderReferencesDoNotBlockEachOther() {
        profitDistributionService.propose(financeGroup.getId(), coordinatorId, proportionalRequest());

        var otherOrder = new ProposeProfitDistributionRequest(List.of("Order #999"), new BigDecimal("50.00"), List.of(
                new RecipientInput(creatorAId, 1, null)));
        ProfitDistributionView other = profitDistributionService.propose(financeGroup.getId(), coordinatorId, otherOrder);

        assertThat(other.status()).isEqualTo(ApprovalStatus.PENDING);
    }

    @Test
    void aProposalCanCoverSeveralOrdersAtOnce() {
        var request = new ProposeProfitDistributionRequest(
                List.of("Order #201", "Order #202", "Order #203"), new BigDecimal("300.00"), List.of(
                        new RecipientInput(creatorAId, 2, null),
                        new RecipientInput(creatorBId, 1, null)));

        ProfitDistributionView proposed = profitDistributionService.propose(financeGroup.getId(), coordinatorId, request);

        assertThat(proposed.orderReferences()).containsExactly("Order #201", "Order #202", "Order #203");
    }

    @Test
    void anEmptyOrderReferenceListIsAGeneralSettlementNotAnError() {
        var request = new ProposeProfitDistributionRequest(List.of(), new BigDecimal("90.00"), List.of(
                new RecipientInput(creatorAId, 1, null)));

        ProfitDistributionView proposed = profitDistributionService.propose(financeGroup.getId(), coordinatorId, request);

        assertThat(proposed.orderReferences()).isEmpty();
    }

    @Test
    void multipleGeneralSettlementsCanBePendingAtOnceSinceNoneAreTiedToASpecificOrder() {
        var first = new ProposeProfitDistributionRequest(List.of(), new BigDecimal("90.00"), List.of(
                new RecipientInput(creatorAId, 1, null)));
        var second = new ProposeProfitDistributionRequest(List.of(), new BigDecimal("40.00"), List.of(
                new RecipientInput(creatorBId, 1, null)));

        profitDistributionService.propose(financeGroup.getId(), coordinatorId, first);
        ProfitDistributionView secondProposed = profitDistributionService.propose(financeGroup.getId(), coordinatorId, second);

        assertThat(secondProposed.status()).isEqualTo(ApprovalStatus.PENDING);
    }
}
