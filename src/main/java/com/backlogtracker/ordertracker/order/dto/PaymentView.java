package com.backlogtracker.ordertracker.order.dto;

import java.time.Instant;

import com.backlogtracker.ordertracker.order.domain.Payment;
import com.backlogtracker.ordertracker.order.domain.PaymentMode;

public record PaymentView(double amount, PaymentMode mode, String note, Instant paidAt) {

    public static PaymentView of(Payment p) {
        return new PaymentView(p.amountValue(), p.modeValue(),
                p.getNote() == null ? null : p.getNote().value(), p.getPaidAt());
    }
}
