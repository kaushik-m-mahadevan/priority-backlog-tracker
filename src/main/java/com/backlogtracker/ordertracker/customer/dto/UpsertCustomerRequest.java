package com.backlogtracker.ordertracker.customer.dto;

import java.time.Instant;
import java.util.List;

import com.backlogtracker.ordertracker.customer.domain.AcquisitionChannel;

public record UpsertCustomerRequest(String name, String contactNumber, String email,
                                    String instagramHandle, AcquisitionChannel acquisitionChannel,
                                    Instant firstContactDate, List<AddressInput> addresses, String notes) {

    /** ad-6: replaces the old single {@code shippingAddress} string — a full replace-list
     *  on every save, same convention as this app's other embedded-list upserts (e.g.
     *  config's priority values). {@code addressId} null means "new entry, assign one". */
    public record AddressInput(String addressId, String label, String address, boolean isDefault) {
    }
}
