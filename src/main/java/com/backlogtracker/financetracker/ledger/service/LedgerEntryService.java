package com.backlogtracker.financetracker.ledger.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.backlogtracker.commons.finance.PaymentSyncEvent;
import com.backlogtracker.commons.group.domain.Group;
import com.backlogtracker.commons.group.service.GroupService;
import com.backlogtracker.commons.web.ScopedLookup;
import com.backlogtracker.financetracker.ledger.domain.LedgerEntry;
import com.backlogtracker.financetracker.ledger.domain.PartyType;
import com.backlogtracker.financetracker.ledger.dto.CreateLedgerEntryRequest;
import com.backlogtracker.financetracker.ledger.dto.CreateLedgerEntryRequest.PartyInput;
import com.backlogtracker.financetracker.ledger.dto.ExternalPartySuggestion;
import com.backlogtracker.financetracker.ledger.dto.LedgerEntryView;
import com.backlogtracker.financetracker.ledger.dto.MemberBalanceView;
import com.backlogtracker.financetracker.ledger.repository.LedgerEntryRepository;

import lombok.RequiredArgsConstructor;

/**
 * ad-1: every real cash movement in a finance group's ledger — always exactly one Debit
 * party and one Credit party (see {@link LedgerEntry}). Full transparency by design (user
 * decision, carried over from the pre-ad-1 ledger): any member can list every entry, not
 * just their own.
 */
@Service
@RequiredArgsConstructor
public class LedgerEntryService {

    private final LedgerEntryRepository entries;
    private final GroupService groupService;
    private final Clock clock;

    public LedgerEntryView create(String groupId, String userId, CreateLedgerEntryRequest request) {
        Group group = groupService.requireMember(groupId, userId);
        LedgerEntry.Party debit = validateParty(group, request.debit());
        LedgerEntry.Party credit = validateParty(group, request.credit());
        requireOrderReferenceIfCustomer(debit, credit, request.orderReference());
        LedgerEntry entry = LedgerEntry.builder()
                .groupId(groupId)
                .date(request.date() == null ? clock.instant() : request.date())
                .description(request.description().trim())
                .amount(request.amount())
                .debit(debit)
                .credit(credit)
                .orderReference(blankToNull(request.orderReference()))
                .notes(blankToNull(request.notes()))
                .createdByUserId(userId)
                .build();
        return LedgerEntryView.of(entries.save(entry));
    }

    /** Full edit for a manually-entered row (description/amount/parties/order
     *  reference/notes, exactly what {@link #create} accepts). A row synced from an Order
     *  Tracker payment ({@code sourceRef} set) stays locked to its source everywhere except
     *  {@code notes} — editing anything else here would silently drift it from the real
     *  payment it mirrors, the same reason its delete doesn't reach back to Order Tracker
     *  either. */
    public LedgerEntryView update(String groupId, String userId, String entryId, CreateLedgerEntryRequest request) {
        Group group = groupService.requireMember(groupId, userId);
        LedgerEntry entry = requireById(groupId, entryId);
        if (entry.getSourceRef() == null) {
            LedgerEntry.Party debit = validateParty(group, request.debit());
            LedgerEntry.Party credit = validateParty(group, request.credit());
            requireOrderReferenceIfCustomer(debit, credit, request.orderReference());
            entry.setDate(request.date() == null ? entry.getDate() : request.date());
            entry.setDescription(request.description().trim());
            entry.setAmount(request.amount());
            entry.setDebit(debit);
            entry.setCredit(credit);
            entry.setOrderReference(blankToNull(request.orderReference()));
        }
        entry.setNotes(blankToNull(request.notes()));
        return LedgerEntryView.of(entries.save(entry));
    }

    private static void requireOrderReferenceIfCustomer(LedgerEntry.Party debit, LedgerEntry.Party credit, String orderReference) {
        // A payment involving a customer is money moving against a specific order — leaving
        // it untied would make the ledger untraceable back to the order it actually belongs
        // to, unlike a plain member/business/external transaction (rent, supplies, ...).
        boolean blank = orderReference == null || orderReference.isBlank();
        if (blank && (debit.getType() == PartyType.CUSTOMER || credit.getType() == PartyType.CUSTOMER)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Order reference is required for a payment involving a customer");
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    /** Manual entries and auto-synced ones alike (design decision: this stays a simple
     *  ledger a member can correct, not audited accounting software) — deleting a synced
     *  row here does *not* reach back to Order Tracker; use the order's own "remove
     *  payment" for that, which removes the ledger row through {@link #removeSyncedPayment}
     *  instead so both sides always agree on why a row disappeared. */
    public void delete(String groupId, String userId, String entryId) {
        groupService.requireMember(groupId, userId);
        entries.delete(requireById(groupId, entryId));
    }

    public List<LedgerEntryView> list(String groupId, String userId) {
        groupService.requireMember(groupId, userId);
        return entries.findByGroupIdOrderByDateDesc(groupId).stream().map(LedgerEntryView::of).toList();
    }

    /** One row per member who appears as a Debit or Credit party anywhere in the ledger —
     *  full transparency (any member can compute anyone's balance), always live, never
     *  stored. */
    public List<MemberBalanceView> balances(String groupId, String userId) {
        groupService.requireMember(groupId, userId);
        Map<String, BigDecimal> net = new LinkedHashMap<>();
        for (LedgerEntry e : entries.findByGroupIdOrderByDateDesc(groupId)) {
            if (e.getCredit().getType() == PartyType.MEMBER) {
                net.merge(e.getCredit().getUserId(), e.getAmount(), BigDecimal::add);
            }
            if (e.getDebit().getType() == PartyType.MEMBER) {
                net.merge(e.getDebit().getUserId(), e.getAmount().negate(), BigDecimal::add);
            }
        }
        return net.entrySet().stream().map(en -> new MemberBalanceView(en.getKey(), en.getValue())).toList();
    }

    /** Every distinct EXTERNAL display name ever used in this group, most-used first — the
     *  "suggestions dropdown of previously-typed external names" the spec asks for. */
    public List<ExternalPartySuggestion> externalSuggestions(String groupId, String userId) {
        groupService.requireMember(groupId, userId);
        Map<String, Long> counts = new LinkedHashMap<>();
        for (LedgerEntry e : entries.findByGroupIdOrderByDateDesc(groupId)) {
            countExternal(counts, e.getDebit());
            countExternal(counts, e.getCredit());
        }
        return counts.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .map(en -> new ExternalPartySuggestion(en.getKey(), en.getValue()))
                .toList();
    }

    private static void countExternal(Map<String, Long> counts, LedgerEntry.Party party) {
        if (party.getType() == PartyType.EXTERNAL && party.getDisplayName() != null) {
            counts.merge(party.getDisplayName(), 1L, Long::sum);
        }
    }

    // ---- ad-1: cross-applet sync — called by FinanceTrackerPaymentSyncConsumer, which
    // implements commons.finance.PaymentSyncConsumer for Order Tracker to call into. ----

    /** Idempotent on {@code event.sourceRef()} — a repeat delivery (a backfill re-run, a
     *  retried request) is a no-op past the first insert. */
    public void syncFromOrderPayment(String groupId, String userId, PaymentSyncEvent event) {
        if (entries.findByGroupIdAndSourceRef(groupId, event.sourceRef()).isPresent()) {
            return;
        }
        entries.save(LedgerEntry.builder()
                .groupId(groupId)
                .date(event.date())
                .description(event.description())
                .amount(BigDecimal.valueOf(event.amount()))
                .debit(toParty(event.debit()))
                .credit(toParty(event.credit()))
                .sourceRef(event.sourceRef())
                .orderReference(event.orderReference())
                .createdByUserId(userId)
                .build());
    }

    /** No-op if nothing was ever synced under this {@code sourceRef} (e.g. the payment was
     *  recorded before this business was linked). */
    public void removeSyncedPayment(String groupId, String sourceRef) {
        entries.deleteByGroupIdAndSourceRef(groupId, sourceRef);
    }

    private static LedgerEntry.Party toParty(PaymentSyncEvent.PartyRef ref) {
        return switch (ref.kind()) {
            case "MEMBER" -> LedgerEntry.Party.builder().type(PartyType.MEMBER).userId(ref.userId()).build();
            case "BUSINESS" -> LedgerEntry.Party.builder().type(PartyType.BUSINESS).build();
            case "CUSTOMER" -> LedgerEntry.Party.builder().type(PartyType.CUSTOMER).displayName(ref.displayName()).build();
            default -> LedgerEntry.Party.builder().type(PartyType.EXTERNAL).displayName(ref.displayName()).build();
        };
    }

    private LedgerEntry.Party validateParty(Group group, PartyInput input) {
        return switch (input.type()) {
            case MEMBER -> {
                if (input.userId() == null || !group.hasMember(input.userId())) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "userId must be a member of this finance group");
                }
                yield LedgerEntry.Party.builder().type(PartyType.MEMBER).userId(input.userId()).build();
            }
            case BUSINESS -> LedgerEntry.Party.builder().type(PartyType.BUSINESS).build();
            case CUSTOMER, EXTERNAL -> {
                if (input.displayName() == null || input.displayName().isBlank()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "displayName is required for this party type");
                }
                yield LedgerEntry.Party.builder().type(input.type()).displayName(input.displayName().trim()).build();
            }
        };
    }

    private LedgerEntry requireById(String groupId, String entryId) {
        return ScopedLookup.requireInGroup(entries.findById(entryId), LedgerEntry::getGroupId, groupId, "Ledger entry not found");
    }
}
