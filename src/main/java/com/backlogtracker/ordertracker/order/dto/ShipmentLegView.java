package com.backlogtracker.ordertracker.order.dto;

import java.time.Instant;

import com.backlogtracker.ordertracker.order.domain.ShipmentLeg;

public record ShipmentLegView(String originLocationCode, String destinationLocationCode, String carrier,
                              String trackingNumber, double estimatedCost, double estimatedTimeHours,
                              Instant shippedAt, Instant deliveredAt) {

    public static ShipmentLegView of(ShipmentLeg leg) {
        return new ShipmentLegView(leg.getOriginLocationCode(), leg.getDestinationLocationCode(), leg.getCarrier(),
                leg.getTrackingNumber() == null ? null : leg.getTrackingNumber().value(),
                leg.getEstimatedCost(), leg.getEstimatedTimeHours(), leg.getShippedAt(), leg.getDeliveredAt());
    }
}
