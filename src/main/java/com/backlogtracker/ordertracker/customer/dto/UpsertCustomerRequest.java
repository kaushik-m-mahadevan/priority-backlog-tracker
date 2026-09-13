package com.backlogtracker.ordertracker.customer.dto;

import java.time.Instant;

import com.backlogtracker.ordertracker.customer.domain.AcquisitionChannel;

public record UpsertCustomerRequest(String name, String contactNumber, String email,
                                    String instagramHandle, AcquisitionChannel acquisitionChannel,
                                    Instant firstContactDate, String shippingAddress, String notes) {
}
