package com.backlogtracker.financetracker.profitsplit.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.backlogtracker.commons.approval.domain.ApprovalRequest;
import com.backlogtracker.commons.approval.domain.ApprovalStatus;
import com.backlogtracker.commons.approval.event.ApprovalRequestInvalidatedEvent;
import com.backlogtracker.commons.approval.repository.ApprovalRequestRepository;
import com.backlogtracker.commons.approval.service.ApprovalService;
import com.backlogtracker.commons.finance.OrderSplitLookup;
import com.backlogtracker.commons.group.domain.Group;
import com.backlogtracker.commons.group.service.GroupService;
import com.backlogtracker.commons.link.service.GroupLinkService;
import com.backlogtracker.commons.notification.domain.NotificationType;
import com.backlogtracker.commons.notification.service.NotificationOrchestrator;
import com.backlogtracker.commons.notification.service.NotificationService;
import com.backlogtracker.financetracker.ledger.domain.PartyType;
import com.backlogtracker.financetracker.ledger.dto.CreateLedgerEntryRequest;
import com.backlogtracker.financetracker.ledger.dto.CreateLedgerEntryRequest.PartyInput;
import com.backlogtracker.financetracker.ledger.service.LedgerEntryService;
import com.backlogtracker.financetracker.profitsplit.dto.ProfitDistributionView;
import com.backlogtracker.financetracker.profitsplit.dto.ProfitDistributionView.RecipientAmountView;
import com.backlogtracker.financetracker.profitsplit.dto.ProposeProfitDistributionRequest;
import com.backlogtracker.financetracker.profitsplit.dto.ProposeProfitDistributionRequest.RecipientInput;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Distributes an order's profit among the people who worked on it, gated behind the same
 * unanimous-approval consensus as order-finalization (design decision: "we need all to
 * accept ... a profit split"). {@code totalProfit} is entered manually — typically read
 * off the order's finalized numbers in Order Tracker — rather than fetched live, since
 * Finance Tracker and Order Tracker are separate applets linked only by {@code GroupLink};
 * this keeps the split self-contained without adding cross-applet data coupling.
 *
 * <p>Once unanimously approved, one INCOME {@code LedgerEntry} is created per recipient,
 * crediting their share — the business now owes them that amount, same mechanism as
 * crediting an investor (see {@link LedgerEntryService}).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProfitDistributionService {

    private static final String KIND_PREFIX = "financetracker:profit-split:";

    private final ApprovalService approvalService;
    private final ApprovalRequestRepository approvalRequests;
    private final GroupService groupService;
    private final GroupLinkService groupLinkService;
    private final List<OrderSplitLookup> orderSplitLookups;
    private final LedgerEntryService ledgerEntryService;
    private final NotificationService notificationService;
    private final NotificationOrchestrator notificationOrchestrator;

    /** mb-18: pre-fills a proposal's recipient/unit split from a real linked order's own
     *  split allocation, instead of it being re-typed by hand — the coordinator can still
     *  edit anything this returns before proposing (same manual-override path as before). */
    public OrderSplitLookup.OrderSplitView lookupOrderSplit(String groupId, String userId, String reference) {
        groupService.requireMember(groupId, userId);
        String orderGroupId = groupLinkService.linkedGroupId(groupId, userId, Group.APPLET_ORDER_TRACKER)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Link an Order Tracker business to this group first"));
        return orderSplitLookup()
                .flatMap(lookup -> lookup.findByReference(orderGroupId, userId, reference))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No order found for reference '" + reference + "'"));
    }

    private java.util.Optional<OrderSplitLookup> orderSplitLookup() {
        return orderSplitLookups.stream().filter(l -> Group.APPLET_ORDER_TRACKER.equals(l.appletKey())).findFirst();
    }

    public List<ProfitDistributionView> list(String groupId, String userId) {
        Group group = groupService.requireMember(groupId, userId);
        return approvalRequests.findByGroupId(groupId).stream()
                .filter(r -> r.getKind().startsWith(KIND_PREFIX))
                .map(r -> view(r, group))
                .toList();
    }

    public ProfitDistributionView get(String groupId, String userId, String requestId) {
        Group group = groupService.requireMember(groupId, userId);
        return view(requireRequest(groupId, requestId), group);
    }

    public ProfitDistributionView propose(String groupId, String userId, ProposeProfitDistributionRequest request) {
        Group group = groupService.requireMember(groupId, userId);
        List<String> orderReferences = normalizeReferences(request.orderReferences());
        if (request.totalProfit() == null || request.totalProfit().signum() < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "totalProfit must be zero or positive");
        }
        Map<String, BigDecimal> amounts = computeAmounts(group, request);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderReferences", orderReferences);
        payload.put("totalProfit", request.totalProfit());
        payload.put("recipients", request.recipients().stream()
                .map(r -> Map.of("personId", (Object) r.personId(), "amount", (Object) amounts.get(r.personId())))
                .toList());

        ApprovalRequest approval = approvalService.propose(groupId, userId, kind(orderReferences), payload);
        if (approval.getStatus() == ApprovalStatus.APPROVED) {
            applyDistribution(groupId, userId, approval);
        } else {
            notificationOrchestrator.notifyOtherMembers(group, userId, NotificationType.PROFIT_DISTRIBUTION_PROPOSED,
                    "Profit distribution", "A new profit distribution proposal is waiting for your approval.",
                    "/financetracker/profit-split");
        }
        return view(approval, group);
    }

    /** Trims and drops blanks; an empty result is a valid "general settlement" not tied to
     *  any specific order, not an error (design decision, round 5 review) — the proposer
     *  might genuinely be splitting profit across several orders they don't want to type
     *  out individually, or not remember which order this covers at all. */
    private static List<String> normalizeReferences(List<String> raw) {
        if (raw == null) {
            return List.of();
        }
        return raw.stream().filter(s -> s != null && !s.isBlank()).map(String::trim).toList();
    }

    public ProfitDistributionView approve(String groupId, String userId, String requestId) {
        Group group = groupService.requireMember(groupId, userId);
        ApprovalRequest approval = approvalService.approve(groupId, userId, requestId);
        if (approval.getStatus() == ApprovalStatus.APPROVED) {
            applyDistribution(groupId, approval.getProposedByUserId(), approval);
        }
        return view(approval, group);
    }

    public ProfitDistributionView reject(String groupId, String userId, String requestId) {
        Group group = groupService.requireMember(groupId, userId);
        ApprovalRequest approval = approvalService.reject(groupId, userId, requestId);
        return view(approval, group);
    }

    /** Creates one INCOME ledger entry per recipient, attributed to whoever proposed the
     *  split (a valid group member for {@link LedgerEntryService}'s own authorization
     *  check, regardless of who cast the final approving vote). */
    @SuppressWarnings("unchecked")
    private void applyDistribution(String groupId, String proposedByUserId, ApprovalRequest approval) {
        List<String> orderReferences = (List<String>) approval.getPayload().getOrDefault("orderReferences", List.of());
        String label = orderReferences.isEmpty() ? "General settlement" : String.join(", ", orderReferences);
        List<Map<String, Object>> recipients = (List<Map<String, Object>>) approval.getPayload().get("recipients");
        for (Map<String, Object> recipient : recipients) {
            String personId = (String) recipient.get("personId");
            BigDecimal amount = toBigDecimal(recipient.get("amount"));
            if (amount.signum() <= 0) {
                continue;
            }
            ledgerEntryService.create(groupId, proposedByUserId, new CreateLedgerEntryRequest(
                    null,
                    "Profit distribution: " + label,
                    amount,
                    new PartyInput(PartyType.BUSINESS, null, null),
                    new PartyInput(PartyType.MEMBER, personId, null),
                    orderReferences.isEmpty() ? null : label, null));
        }
    }

    private static BigDecimal toBigDecimal(Object value) {
        if (value instanceof BigDecimal bd) {
            return bd;
        }
        return new BigDecimal(value.toString());
    }

    /** A member leaving mid-approval invalidates the underlying ApprovalRequest generically
     *  — nothing else to reset here (unlike order-finalization, there's no denormalized
     *  state on another document), just notify the proposer, mirroring the other two
     *  approval-backed flows. */
    @EventListener
    public void onApprovalInvalidated(ApprovalRequestInvalidatedEvent event) {
        if (!event.kind().startsWith(KIND_PREFIX)) {
            return;
        }
        notificationService.info(event.proposedByUserId(), NotificationType.PROFIT_DISTRIBUTION_INVALIDATED,
                "Profit distribution", "Your proposed profit distribution was cancelled because a member left "
                        + "the group mid-approval. You can propose it again.", "/financetracker/profit-split");
        log.info("Profit distribution request {} invalidated", event.requestId());
    }

    private Map<String, BigDecimal> computeAmounts(Group group, ProposeProfitDistributionRequest request) {
        List<RecipientInput> recipients = request.recipients();
        if (recipients == null || recipients.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At least one recipient is required");
        }
        Set<String> seen = new HashSet<>();
        BigDecimal overrideSum = BigDecimal.ZERO;
        int proportionalUnitsTotal = 0;
        List<RecipientInput> proportional = new ArrayList<>();
        Map<String, BigDecimal> amounts = new LinkedHashMap<>();

        for (RecipientInput r : recipients) {
            if (r.personId() == null || r.personId().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Every recipient needs a personId");
            }
            if (!group.hasMember(r.personId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Recipient personId must be a member of this finance group");
            }
            if (!seen.add(r.personId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The same recipient appears more than once");
            }
            if (r.overrideAmount() != null) {
                if (r.overrideAmount().signum() < 0) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "overrideAmount must not be negative");
                }
                overrideSum = overrideSum.add(r.overrideAmount());
                amounts.put(r.personId(), r.overrideAmount());
            } else {
                if (r.unitsCompleted() <= 0) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "unitsCompleted must be positive for a recipient without an override");
                }
                proportional.add(r);
                proportionalUnitsTotal += r.unitsCompleted();
            }
        }

        BigDecimal remaining = request.totalProfit().subtract(overrideSum);
        if (remaining.signum() < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Overrides add up to more than the total profit");
        }
        if (proportional.isEmpty()) {
            if (remaining.signum() != 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Overrides must add up to the total profit when every recipient has one");
            }
        } else {
            BigDecimal allocated = BigDecimal.ZERO;
            for (int i = 0; i < proportional.size(); i++) {
                RecipientInput r = proportional.get(i);
                BigDecimal amount = i == proportional.size() - 1
                        ? remaining.subtract(allocated).setScale(2, RoundingMode.HALF_UP)
                        : remaining.multiply(BigDecimal.valueOf(r.unitsCompleted()))
                                .divide(BigDecimal.valueOf(proportionalUnitsTotal), 2, RoundingMode.HALF_UP);
                if (i < proportional.size() - 1) {
                    allocated = allocated.add(amount);
                }
                amounts.put(r.personId(), amount);
            }
        }
        return amounts;
    }

    @SuppressWarnings("unchecked")
    private ProfitDistributionView view(ApprovalRequest approval, Group group) {
        List<String> orderReferences = (List<String>) approval.getPayload().getOrDefault("orderReferences", List.of());
        BigDecimal totalProfit = toBigDecimal(approval.getPayload().get("totalProfit"));
        List<Map<String, Object>> recipients = (List<Map<String, Object>>) approval.getPayload().get("recipients");
        List<RecipientAmountView> recipientViews = recipients.stream()
                .map(r -> new RecipientAmountView((String) r.get("personId"), toBigDecimal(r.get("amount"))))
                .toList();
        return new ProfitDistributionView(approval.getId(), approval.getStatus(), orderReferences, totalProfit,
                recipientViews, approval.getProposedByUserId(), approval.getApprovedByUserIds(),
                group.getMemberIds(), approval.getCreatedAt(), approval.getResolvedAt());
    }

    private ApprovalRequest requireRequest(String groupId, String requestId) {
        ApprovalRequest request = approvalRequests.findById(requestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Profit distribution not found"));
        if (!groupId.equals(request.getGroupId()) || !request.getKind().startsWith(KIND_PREFIX)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Profit distribution not found");
        }
        return request;
    }

    /** Order-reference proposals dedup on the same set of references (so you can't have two
     *  pending proposals covering the identical order(s) at once) — sorted so the same set
     *  in a different typing order still collides. A general settlement (no references) has
     *  nothing to dedup against, so each one gets its own random key and any number can be
     *  pending at once. */
    private String kind(List<String> orderReferences) {
        if (orderReferences.isEmpty()) {
            return KIND_PREFIX + "general:" + java.util.UUID.randomUUID();
        }
        return KIND_PREFIX + orderReferences.stream().sorted().reduce((a, b) -> a + "," + b).orElseThrow();
    }
}
