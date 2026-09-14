package com.backlogtracker.ordertracker.master.domain;

public enum CostConfigChangeStatus {
    PENDING, APPROVED, REJECTED,
    /** Auto-cancelled because group membership changed mid-approval — distinct from a
     *  member actively voting REJECTED, since a departure can reasonably change how the
     *  remaining members would have voted (design: a leave restarts the conversation
     *  rather than silently resolving on whoever happened to still be around). */
    INVALIDATED
}
