package com.backlogtracker.materialinventory.inventory.dto;

/** ad-2: one teammate who has stock of the yarn type the caller is short on, sorted by
 *  quantity (most-stocked first) so the biggest, most useful transfer sits at the top.
 *  Ranking by teammate location (nearest first, per spec) is deferred — it needs a second
 *  new cross-applet lookup into Order Tracker's Creator locations beyond the reservation
 *  one this round already adds, and every candidate is still shown regardless, just
 *  unranked by proximity for now. */
public record TransferSuggestionView(String userId, double quantity) {
}
