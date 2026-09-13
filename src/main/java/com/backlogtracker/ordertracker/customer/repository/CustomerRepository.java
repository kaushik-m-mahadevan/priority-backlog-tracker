package com.backlogtracker.ordertracker.customer.repository;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.backlogtracker.ordertracker.customer.domain.Customer;

public interface CustomerRepository extends MongoRepository<Customer, String> {

    List<Customer> findByGroupId(String groupId);
}
