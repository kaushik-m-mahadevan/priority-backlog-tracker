package com.backlogtracker.ordertracker.order.domain;

import java.time.Instant;

import com.backlogtracker.commons.crypto.EncryptedString;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One entry in an order's payment ledger (design §6). {@code amount} and {@code note} are
 * encrypted (financial detail); {@code amount} is stored as the plain decimal string
 * representation inside the {@link EncryptedString} wrapper since encryption is per-field,
 * not per-type, and there is no numeric encrypted wrapper. {@code mode} is also encrypted
 * per §4.5 even though it's a small enum — the raw ciphertext-at-rest field still has to be
 * a String-shaped column, so it's stored as the enum's {@code name()}.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Payment {

    private EncryptedString amount;
    private EncryptedString mode;
    private EncryptedString note;
    private Instant paidAt;

    public double amountValue() {
        return amount == null || amount.value() == null ? 0.0 : Double.parseDouble(amount.value());
    }

    public PaymentMode modeValue() {
        return mode == null || mode.value() == null ? null : PaymentMode.valueOf(mode.value());
    }

    public static Payment of(double amount, PaymentMode mode, String note, Instant paidAt) {
        return Payment.builder()
                .amount(EncryptedString.of(Double.toString(amount)))
                .mode(EncryptedString.of(mode.name()))
                .note(EncryptedString.of(note))
                .paidAt(paidAt)
                .build();
    }
}
