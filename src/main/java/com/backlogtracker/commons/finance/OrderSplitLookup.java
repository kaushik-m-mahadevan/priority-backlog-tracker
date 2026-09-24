package com.backlogtracker.commons.finance;

import java.util.List;
import java.util.Optional;

/**
 * mb-18: the pull-side counterpart to {@link PaymentSyncConsumer} — implemented by one
 * Spring bean (Order Tracker) so Finance Tracker's profit-split proposal can pre-fill a
 * recipient/unit split from a real order's own split allocation instead of it being
 * re-typed by hand, without importing across applets (same bean-discovery shape as
 * {@link PaymentSyncConsumer} and {@code com.backlogtracker.commons.search.Searchable}).
 */
public interface OrderSplitLookup {

    /** One of the {@code Group.APPLET_*} constants — which applet's groups this lookup can
     *  resolve an order reference against. */
    String appletKey();

    /** {@code reference} is matched against the order's own human-readable order number
     *  (the same free-text value a proposer would otherwise type into
     *  {@code orderReferences}). Empty if no order in this group has that reference. */
    Optional<OrderSplitView> findByReference(String groupId, String userId, String reference);

    /** {@code recipients} are keyed by platform user id (not the order applet's own
     *  Creator id) — already resolved to whatever id space {@code personId} lives in on the
     *  Finance Tracker side, so the caller can use them directly. */
    record OrderSplitView(String reference, List<RecipientSplit> recipients) {
    }

    /** For a bulk order, {@code unitsCompleted} is that person's {@code quantityAssigned}
     *  summed across every variant's split allocation; for an individual order, the single
     *  creator gets {@code unitsCompleted = 1} (there's nothing to split). */
    record RecipientSplit(String userId, int unitsCompleted) {
    }
}
