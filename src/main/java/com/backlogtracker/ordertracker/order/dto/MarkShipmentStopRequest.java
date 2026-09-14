package com.backlogtracker.ordertracker.order.dto;

import java.time.Instant;

public record MarkShipmentStopRequest(Instant shippedDate, Boolean deliveredConfirmed) {
}
