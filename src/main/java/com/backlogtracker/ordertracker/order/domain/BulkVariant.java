package com.backlogtracker.ordertracker.order.domain;

import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One variant within a bulk order (design §8) — e.g. "Small / cream" vs. "Large / grey" —
 *  each with its own quantity and mandatory-item selections. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkVariant {

    private String label;
    private int quantity;
    private Map<String, String> mandatoryItems;
}
