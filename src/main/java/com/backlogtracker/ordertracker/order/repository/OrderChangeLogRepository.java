package com.backlogtracker.ordertracker.order.repository;

import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.backlogtracker.ordertracker.order.domain.OrderChangeLog;

public interface OrderChangeLogRepository extends MongoRepository<OrderChangeLog, String> {

    Optional<OrderChangeLog> findByOrderId(String orderId);
}
