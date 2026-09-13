package com.backlogtracker.ordertracker.order.dto;

import java.time.Instant;

import com.backlogtracker.ordertracker.order.domain.PaymentMode;

/** amount may be negative to record a refund (design §6). */
public record AddPaymentRequest(double amount, PaymentMode mode, String note, Instant paidAt) {
}
