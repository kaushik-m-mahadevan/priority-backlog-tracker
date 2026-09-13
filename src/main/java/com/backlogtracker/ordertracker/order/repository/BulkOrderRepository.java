package com.backlogtracker.ordertracker.order.repository;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.backlogtracker.ordertracker.order.domain.BulkOrder;

public interface BulkOrderRepository extends MongoRepository<BulkOrder, String> {

    List<BulkOrder> findByGroupId(String groupId);
}
