package com.backlogtracker.ordertracker.customer.repository;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.backlogtracker.ordertracker.customer.domain.Customer;

public interface CustomerRepository extends MongoRepository<Customer, String> {

    List<Customer> findByGroupId(String groupId);

    List<Customer> findByGroupIdAndEmailHash(String groupId, String emailHash);

    List<Customer> findByGroupIdAndInstagramHandleHash(String groupId, String instagramHandleHash);

    List<Customer> findByGroupIdAndContactNumberHash(String groupId, String contactNumberHash);
}
