package com.backlogtracker.ordertracker.customer.domain;

import com.backlogtracker.commons.crypto.EncryptedString;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One of a customer's saved addresses (ad-6) — a customer can have several (home, work,
 * a gift-ship-to, ...), each with a short plain-text {@code label} (not PII, so it stays
 * an ordinary String — the same reasoning as {@link Customer#getAcquisitionChannel()})
 * and its own {@link EncryptedString} address text, with at most one marked default.
 * Replaces the old single {@code shippingAddress} field.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerAddress {

    private String addressId;
    private String label;
    private EncryptedString address;
    private boolean isDefault;
}
