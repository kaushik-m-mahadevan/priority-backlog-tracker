package com.backlogtracker.ordertracker.customer.service;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.backlogtracker.commons.crypto.EncryptedString;
import com.backlogtracker.commons.group.service.GroupService;
import com.backlogtracker.ordertracker.customer.domain.Customer;
import com.backlogtracker.ordertracker.customer.dto.CustomerView;
import com.backlogtracker.ordertracker.customer.dto.UpsertCustomerRequest;
import com.backlogtracker.ordertracker.customer.repository.CustomerRepository;

import lombok.RequiredArgsConstructor;

/** CRUD for a group's Order Tracker customers, gated by group membership. */
@Service
@RequiredArgsConstructor
public class CustomerService {

    private final CustomerRepository repository;
    private final GroupService groupService;

    public List<CustomerView> all(String groupId, String userId) {
        groupService.requireMember(groupId, userId);
        return repository.findByGroupId(groupId).stream().map(CustomerView::of).toList();
    }

    public CustomerView get(String groupId, String userId, String customerId) {
        groupService.requireMember(groupId, userId);
        return CustomerView.of(requireById(groupId, customerId));
    }

    public CustomerView create(String groupId, String userId, UpsertCustomerRequest request) {
        groupService.requireMember(groupId, userId);
        Customer customer = Customer.builder()
                .groupId(groupId)
                .name(EncryptedString.of(request.name()))
                .contactNumber(EncryptedString.of(request.contactNumber()))
                .email(EncryptedString.of(request.email()))
                .instagramHandle(EncryptedString.of(request.instagramHandle()))
                .acquisitionChannel(request.acquisitionChannel())
                .firstContactDate(request.firstContactDate())
                .shippingAddress(EncryptedString.of(request.shippingAddress()))
                .notes(EncryptedString.of(request.notes()))
                .build();
        return CustomerView.of(repository.save(customer));
    }

    public CustomerView update(String groupId, String userId, String customerId, UpsertCustomerRequest request) {
        groupService.requireMember(groupId, userId);
        Customer customer = requireById(groupId, customerId);
        customer.setName(EncryptedString.of(request.name()));
        customer.setContactNumber(EncryptedString.of(request.contactNumber()));
        customer.setEmail(EncryptedString.of(request.email()));
        customer.setInstagramHandle(EncryptedString.of(request.instagramHandle()));
        customer.setAcquisitionChannel(request.acquisitionChannel());
        customer.setFirstContactDate(request.firstContactDate());
        customer.setShippingAddress(EncryptedString.of(request.shippingAddress()));
        customer.setNotes(EncryptedString.of(request.notes()));
        return CustomerView.of(repository.save(customer));
    }

    private Customer requireById(String groupId, String customerId) {
        Customer customer = repository.findById(customerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found"));
        if (!groupId.equals(customer.getGroupId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found");
        }
        return customer;
    }
}
