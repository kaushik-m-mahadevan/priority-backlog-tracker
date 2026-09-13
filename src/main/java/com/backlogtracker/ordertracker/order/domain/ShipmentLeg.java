package com.backlogtracker.ordertracker.order.domain;

import java.time.Instant;

import com.backlogtracker.commons.crypto.EncryptedString;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One stop of a (possibly multi-stop) shipment plan (design §7). trackingNumber is
 *  encrypted per §4.5; origin/destination location codes stay plain since they're not
 *  free-text PII and the business view may want to filter/report on lanes travelled. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShipmentLeg {

    private String originLocationCode;
    private String destinationLocationCode;
    private String carrier;
    private EncryptedString trackingNumber;
    private double estimatedCost;
    private double estimatedTimeHours;
    private Instant shippedAt;
    private Instant deliveredAt;
}
