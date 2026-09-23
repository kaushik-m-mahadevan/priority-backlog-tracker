package com.backlogtracker.ordertracker.order.dto;

import java.time.Instant;

import com.backlogtracker.ordertracker.order.domain.PaymentType;

public record AddPaymentRequest(PaymentType type, double amount, Instant date, String mode, String note,
                                String receivedBy) {
}
