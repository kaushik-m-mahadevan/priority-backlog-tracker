package com.backlogtracker.financetracker.ledger.service;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.backlogtracker.commons.group.domain.Group;
import com.backlogtracker.commons.group.service.GroupService;
import com.backlogtracker.financetracker.ledger.domain.LedgerEntry;
import com.backlogtracker.financetracker.ledger.domain.LedgerEntryType;
import com.backlogtracker.financetracker.ledger.domain.Settlement;
import com.backlogtracker.financetracker.ledger.domain.SplitPartyType;
import com.backlogtracker.financetracker.ledger.dto.BalanceView;
import com.backlogtracker.financetracker.ledger.dto.CreateLedgerEntryRequest;
import com.backlogtracker.financetracker.ledger.dto.CreateLedgerEntryRequest.ShareInput;
import com.backlogtracker.financetracker.ledger.dto.CreateSettlementRequest;
import com.backlogtracker.financetracker.ledger.dto.LedgerEntryView;
import com.backlogtracker.financetracker.ledger.dto.SettlementView;
import com.backlogtracker.financetracker.ledger.repository.LedgerEntryRepository;
import com.backlogtracker.financetracker.ledger.repository.SettlementRepository;

import lombok.RequiredArgsConstructor;

/**
 * Every money movement in a finance group's shared ledger goes through here — see
 * {@link LedgerEntry} for why there's no separate reimbursement concept. Full
 * transparency by design (user decision): any member can list every entry, not just
 * their own.
 */
@Service
@RequiredArgsConstructor
public class LedgerEntryService {

    /** Ratios are user-entered decimals (e.g. thirds as 0.33/0.33/0.34) — exact-1.0
     *  equality would reject perfectly reasonable splits to the last representable digit,
     *  so sum-to-1 is checked within this tolerance instead. */
    private static final BigDecimal RATIO_TOLERANCE = new BigDecimal("0.01");

    private final LedgerEntryRepository entries;
    private final SettlementRepository settlements;
    private final GroupService groupService;

    public LedgerEntryView create(String groupId, String userId, CreateLedgerEntryRequest request) {
        Group group = groupService.requireMember(groupId, userId);
        if (!group.hasMember(request.payerId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "payerId must be a member of this finance group");
        }
        validateShares(group, request.shares());

        LedgerEntry entry = LedgerEntry.builder()
                .groupId(groupId)
                .type(request.type())
                .description(request.description().trim())
                .amount(request.amount())
                .payerId(request.payerId())
                .shares(request.shares().stream().map(s -> LedgerEntry.SplitShare.builder()
                        .partyType(s.partyType())
                        .personId(s.partyType() == SplitPartyType.PERSON ? s.personId() : null)
                        .ratio(s.ratio())
                        .build()).toList())
                .createdByUserId(userId)
                .build();
        return LedgerEntryView.of(entries.save(entry));
    }

    public List<LedgerEntryView> list(String groupId, String userId) {
        groupService.requireMember(groupId, userId);
        return entries.findByGroupIdOrderByCreatedAtDesc(groupId).stream().map(LedgerEntryView::of).toList();
    }

    public List<SettlementView> listSettlements(String groupId, String userId) {
        groupService.requireMember(groupId, userId);
        return settlements.findByGroupId(groupId).stream().map(SettlementView::of).toList();
    }

    /** See {@link BalanceView} for the two-number shape and why they're kept separate.
     *  Every group member can compute every other member's balance, not just their own
     *  (full transparency, per design decision) — this returns one row per member who
     *  appears anywhere in the ledger or a settlement, in the order first encountered. */
    public List<BalanceView> balances(String groupId, String userId) {
        groupService.requireMember(groupId, userId);
        return computeBalances(groupId);
    }

    /** Records that {@code request.fromPersonId()} (or the business) paid the caller
     *  {@code request.amount()} — only the caller, i.e. the person actually owed, may do
     *  this (design decision: only the person owed money can mark it settled). Rejected
     *  if it would settle more than the caller's current aggregate balance with that
     *  source, so a typo can't manufacture a debt that was never owed. */
    public SettlementView settleUp(String groupId, String userId, CreateSettlementRequest request) {
        Group group = groupService.requireMember(groupId, userId);
        if (request.fromPartyType() == SplitPartyType.PERSON) {
            if (request.fromPersonId() == null || request.fromPersonId().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "fromPersonId is required when fromPartyType is PERSON");
            }
            if (!group.hasMember(request.fromPersonId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "fromPersonId must be a member of this finance group");
            }
            if (request.fromPersonId().equals(userId)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot settle up with yourself");
            }
        } else if (request.fromPersonId() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "fromPersonId must not be set when fromPartyType is BUSINESS");
        }

        Map<String, BalanceView> current = computeBalances(groupId).stream()
                .collect(java.util.stream.Collectors.toMap(BalanceView::personId, b -> b));
        BalanceView mine = current.get(userId);
        BigDecimal owedToMe = request.fromPartyType() == SplitPartyType.BUSINESS
                ? (mine == null ? BigDecimal.ZERO : mine.owedByBusiness())
                : (mine == null ? BigDecimal.ZERO : mine.netFromOthers().max(BigDecimal.ZERO));
        if (request.amount().compareTo(owedToMe) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "That's more than is currently owed to you (" + owedToMe + ")");
        }

        Settlement settlement = Settlement.builder()
                .groupId(groupId)
                .fromPartyType(request.fromPartyType())
                .fromPersonId(request.fromPartyType() == SplitPartyType.PERSON ? request.fromPersonId() : null)
                .toPersonId(userId)
                .amount(request.amount())
                .createdByUserId(userId)
                .build();
        return SettlementView.of(settlements.save(settlement));
    }

    private List<BalanceView> computeBalances(String groupId) {
        Map<String, BigDecimal> netFromOthers = new LinkedHashMap<>();
        Map<String, BigDecimal> owedByBusiness = new LinkedHashMap<>();

        for (LedgerEntry entry : entries.findByGroupIdOrderByCreatedAtDesc(groupId)) {
            for (LedgerEntry.SplitShare share : entry.getShares()) {
                BigDecimal shareAmount = entry.getAmount().multiply(share.getRatio());
                if (entry.getType() == LedgerEntryType.EXPENSE) {
                    if (share.getPartyType() == SplitPartyType.BUSINESS) {
                        add(owedByBusiness, entry.getPayerId(), shareAmount);
                    } else if (!share.getPersonId().equals(entry.getPayerId())) {
                        // this person owes the payer their share; the payer is owed it.
                        add(netFromOthers, share.getPersonId(), shareAmount.negate());
                        add(netFromOthers, entry.getPayerId(), shareAmount);
                    }
                    // a PERSON share equal to the payer is their own absorbed cost — no effect.
                } else if (entry.getType() == LedgerEntryType.INCOME && share.getPartyType() == SplitPartyType.PERSON) {
                    // credited to a specific person (e.g. an investment) — same "the
                    // business owes it back" relationship as a business-attributed expense.
                    add(owedByBusiness, share.getPersonId(), shareAmount);
                }
                // INCOME credited to BUSINESS is the business's own money — no personal balance effect.
            }
        }

        // Settlements apply on top, symmetric to a share: money from A to B reduces A's
        // debt and reduces what's owed to B by the same amount.
        for (Settlement s : settlements.findByGroupId(groupId)) {
            if (s.getFromPartyType() == SplitPartyType.BUSINESS) {
                add(owedByBusiness, s.getToPersonId(), s.getAmount().negate());
            } else {
                add(netFromOthers, s.getFromPersonId(), s.getAmount());
                add(netFromOthers, s.getToPersonId(), s.getAmount().negate());
            }
        }

        Map<String, BigDecimal> zero = new LinkedHashMap<>();
        netFromOthers.keySet().forEach(id -> zero.putIfAbsent(id, BigDecimal.ZERO));
        owedByBusiness.keySet().forEach(id -> zero.putIfAbsent(id, BigDecimal.ZERO));
        return zero.keySet().stream()
                .map(id -> new BalanceView(id,
                        netFromOthers.getOrDefault(id, BigDecimal.ZERO),
                        owedByBusiness.getOrDefault(id, BigDecimal.ZERO)))
                .toList();
    }

    private static void add(Map<String, BigDecimal> map, String key, BigDecimal delta) {
        map.merge(key, delta, BigDecimal::add);
    }

    private void validateShares(Group group, List<ShareInput> shares) {
        boolean businessSeen = false;
        java.util.Set<String> personIdsSeen = new java.util.HashSet<>();
        BigDecimal sum = BigDecimal.ZERO;
        for (ShareInput s : shares) {
            if (s.ratio().signum() <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Every share's ratio must be greater than 0");
            }
            if (s.partyType() == SplitPartyType.PERSON) {
                if (s.personId() == null || s.personId().isBlank()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A PERSON share needs a personId");
                }
                if (!group.hasMember(s.personId())) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Share personId must be a member of this finance group");
                }
                if (!personIdsSeen.add(s.personId())) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "The same person appears in more than one share");
                }
            } else {
                if (s.personId() != null) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A BUSINESS share must not carry a personId");
                }
                if (businessSeen) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "BUSINESS appears in more than one share");
                }
                businessSeen = true;
            }
            sum = sum.add(s.ratio());
        }
        if (sum.subtract(BigDecimal.ONE).abs().compareTo(RATIO_TOLERANCE) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Share ratios must add up to 1 (got " + sum + ")");
        }
    }
}
