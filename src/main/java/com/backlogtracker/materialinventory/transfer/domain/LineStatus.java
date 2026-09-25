package com.backlogtracker.materialinventory.transfer.domain;

/** A transfer request line's own lifecycle, independent of the request as a whole (which
 *  now has no single status — see {@link TransferRequest}'s own doc comment). */
public enum LineStatus {
    /** The target may still send more against this line. */
    OPEN,
    /** The requester has decided they don't need any more of this yarn type from this
     *  target — the target can no longer send against it, but any shipment already sent
     *  stays receivable regardless (closing never undoes what's already in transit). */
    CLOSED
}
