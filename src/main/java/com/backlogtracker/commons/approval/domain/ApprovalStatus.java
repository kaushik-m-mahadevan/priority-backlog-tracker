package com.backlogtracker.commons.approval.domain;

public enum ApprovalStatus {
    PENDING,
    APPROVED,
    REJECTED,
    /** A member left the group mid-approval — cancelled outright rather than silently
     *  resolved on whoever's left, since that could reasonably change how the vote
     *  would have gone. The proposer can simply propose again. */
    INVALIDATED
}
