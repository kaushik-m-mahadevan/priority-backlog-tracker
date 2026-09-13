package com.backlogtracker.ordertracker.order.repository;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.backlogtracker.ordertracker.order.domain.Order;

public interface OrderRepository extends MongoRepository<Order, String> {

    List<Order> findByGroupId(String groupId);
}
