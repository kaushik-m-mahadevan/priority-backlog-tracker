package com.backlogtracker.ordertracker.customer.dto;

import java.time.Instant;
import java.util.List;

import com.backlogtracker.ordertracker.customer.domain.AcquisitionChannel;
import com.backlogtracker.ordertracker.customer.domain.Customer;
import com.backlogtracker.ordertracker.customer.domain.CustomerAddress;

/** Decrypted projection for API responses — the boundary where EncryptedString becomes a
 *  plain String again. */
public record CustomerView(String id, String name, String contactNumber, String email,
                           String instagramHandle, AcquisitionChannel acquisitionChannel,
                           Instant firstContactDate, List<AddressView> addresses, String notes) {

    /** ad-6: replaces the old single {@code shippingAddress} string. */
    public record AddressView(String addressId, String label, String address, boolean isDefault) {
        static AddressView of(CustomerAddress a) {
            return new AddressView(a.getAddressId(), a.getLabel(), value(a.getAddress()), a.isDefault());
        }
    }

    public static CustomerView of(Customer c) {
        return new CustomerView(
                c.getId(),
                value(c.getName()),
                value(c.getContactNumber()),
                value(c.getEmail()),
                value(c.getInstagramHandle()),
                c.getAcquisitionChannel(),
                c.getFirstContactDate(),
                // Defensive against a pre-ad-6 document that has no addresses field at all
                // (Spring Data leaves the field as whatever the no-args constructor left it).
                (c.getAddresses() == null ? List.<CustomerAddress>of() : c.getAddresses()).stream().map(AddressView::of).toList(),
                value(c.getNotes()));
    }

    private static String value(com.backlogtracker.commons.crypto.EncryptedString s) {
        return s == null ? null : s.value();
    }
}
