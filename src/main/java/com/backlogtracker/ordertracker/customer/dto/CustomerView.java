package com.backlogtracker.ordertracker.customer.dto;

import java.time.Instant;

import com.backlogtracker.ordertracker.customer.domain.AcquisitionChannel;
import com.backlogtracker.ordertracker.customer.domain.Customer;

/** Decrypted projection for API responses — the boundary where EncryptedString becomes a
 *  plain String again. */
public record CustomerView(String id, String name, String contactNumber, String email,
                           String instagramHandle, AcquisitionChannel acquisitionChannel,
                           Instant firstContactDate, String shippingAddress, String notes) {

    public static CustomerView of(Customer c) {
        return new CustomerView(
                c.getId(),
                value(c.getName()),
                value(c.getContactNumber()),
                value(c.getEmail()),
                value(c.getInstagramHandle()),
                c.getAcquisitionChannel(),
                c.getFirstContactDate(),
                value(c.getShippingAddress()),
                value(c.getNotes()));
    }

    private static String value(com.backlogtracker.commons.crypto.EncryptedString s) {
        return s == null ? null : s.value();
    }
}
