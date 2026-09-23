package com.backlogtracker.commons.finance;

/**
 * Implemented by one Spring bean (Finance Tracker) so Order Tracker's payment flow (ad-1)
 * can create/remove the matching ledger row without importing across applets — same
 * bean-discovery shape as {@link com.backlogtracker.commons.search.Searchable} and ad-2's
 * {@code InventoryUsageConsumer}/{@code ReservationProvider}.
 */
public interface PaymentSyncConsumer {

    /** One of the {@code Group.APPLET_*} constants — which applet's groups this consumer
     *  can sync payments into. */
    String appletKey();

    /** Idempotent by {@code event.sourceRef()} — safe to call more than once for the same
     *  payment (a backfill re-run, a retried request). */
    void onPaymentRecorded(String groupId, String userId, PaymentSyncEvent event);

    /** No-op if nothing was ever synced under {@code sourceRef} — safe to call for a
     *  payment that was recorded before this business was linked to a Finance group. */
    void onPaymentRemoved(String groupId, String userId, String sourceRef);
}
