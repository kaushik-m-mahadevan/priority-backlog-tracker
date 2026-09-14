package com.backlogtracker.ordertracker.order.dto;

import java.time.Instant;
import java.util.List;

import com.backlogtracker.ordertracker.order.domain.Order.ShipmentStopType;

public record ShipmentPlanRequest(List<StopInput> stops) {

    public record StopInput(int stopOrder, ShipmentStopType type, String originLocationCode,
                            String destinationLocationCode, String laneId, double estimatedCost,
                            double estimatedTimeHours, String carrier, String trackingNumber,
                            Instant triggerDate) {
    }
}
