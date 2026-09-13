package com.backlogtracker.ordertracker.order.domain;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One entry in an order's per-field change history (design §12). Values are stored as
 *  plain strings for display, not the raw domain types — this is an audit trail, not a
 *  source of truth to reconstruct exact previous state from. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChangeLogEntry {

    private String field;
    private String oldValue;
    private String newValue;
    private String changedByUserId;
    private Instant changedAt;
}
