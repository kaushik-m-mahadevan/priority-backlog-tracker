package com.backlogtracker.ordertracker.customer.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.backlogtracker.commons.crypto.BlindIndexService;
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
    private final BlindIndexService blindIndex;

    public List<CustomerView> all(String groupId, String userId) {
        groupService.requireMember(groupId, userId);
        return repository.findByGroupId(groupId).stream().map(CustomerView::of).toList();
    }

    public CustomerView get(String groupId, String userId, String customerId) {
        groupService.requireMember(groupId, userId);
        return CustomerView.of(requireById(groupId, customerId));
    }

    /**
     * Blind-index lookup so the order form can offer "use this existing customer" as soon
     * as a matching email/IG handle/phone is entered, without ever having to decrypt every
     * customer to compare (spec §4.5's ciphertext-is-unqueryable trade-off, worked around
     * for exactly this one case via a deterministic hash — see {@link BlindIndexService}).
     * Matches across whichever of the three fields are provided are unioned, de-duplicated
     * by customer id.
     */
    public List<CustomerView> search(String groupId, String userId, String email, String instagramHandle,
                                     String contactNumber) {
        groupService.requireMember(groupId, userId);
        Map<String, Customer> matches = new LinkedHashMap<>();
        String emailHash = blindIndex.hash(email);
        if (emailHash != null) {
            repository.findByGroupIdAndEmailHash(groupId, emailHash).forEach(c -> matches.put(c.getId(), c));
        }
        String igHash = blindIndex.hash(instagramHandle);
        if (igHash != null) {
            repository.findByGroupIdAndInstagramHandleHash(groupId, igHash).forEach(c -> matches.put(c.getId(), c));
        }
        String phoneHash = blindIndex.hash(contactNumber);
        if (phoneHash != null) {
            repository.findByGroupIdAndContactNumberHash(groupId, phoneHash).forEach(c -> matches.put(c.getId(), c));
        }
        return matches.values().stream().map(CustomerView::of).toList();
    }

    public CustomerView create(String groupId, String userId, UpsertCustomerRequest request) {
        groupService.requireMember(groupId, userId);
        Customer customer = new Customer();
        customer.setGroupId(groupId);
        applyFields(customer, request);
        return CustomerView.of(repository.save(customer));
    }

    public CustomerView update(String groupId, String userId, String customerId, UpsertCustomerRequest request) {
        groupService.requireMember(groupId, userId);
        Customer customer = requireById(groupId, customerId);
        applyFields(customer, request);
        return CustomerView.of(repository.save(customer));
    }

    /** The 8 upsertable fields, shared between create (a blank Customer with just groupId
     *  set) and update (an existing one) — same fields, same encryption/hashing, just a
     *  different starting document. */
    private void applyFields(Customer customer, UpsertCustomerRequest request) {
        customer.setName(EncryptedString.of(request.name()));
        customer.setContactNumber(EncryptedString.of(request.contactNumber()));
        customer.setEmail(EncryptedString.of(request.email()));
        customer.setInstagramHandle(EncryptedString.of(request.instagramHandle()));
        customer.setAcquisitionChannel(request.acquisitionChannel());
        customer.setFirstContactDate(request.firstContactDate());
        customer.setShippingAddress(EncryptedString.of(request.shippingAddress()));
        customer.setNotes(EncryptedString.of(request.notes()));
        customer.setEmailHash(blindIndex.hash(request.email()));
        customer.setInstagramHandleHash(blindIndex.hash(request.instagramHandle()));
        customer.setContactNumberHash(blindIndex.hash(request.contactNumber()));
    }

    /** Existence + group-scoping check only — used by other Order Tracker services that
     *  need to validate a customerId without needing the decrypted view. */
    public void requireExists(String groupId, String customerId) {
        requireById(groupId, customerId);
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
