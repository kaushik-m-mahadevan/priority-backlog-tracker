package com.backlogtracker.ordertracker.order.dto;

public record ShipmentLegRequest(String originLocationCode, String destinationLocationCode, String carrier,
                                 String trackingNumber, double estimatedCost, double estimatedTimeHours) {
}
