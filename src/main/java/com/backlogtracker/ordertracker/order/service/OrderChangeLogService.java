package com.backlogtracker.ordertracker.order.service;

import java.util.List;
import java.util.function.Consumer;

import org.springframework.stereotype.Service;

import com.backlogtracker.ordertracker.order.domain.OrderChangeLog;
import com.backlogtracker.ordertracker.order.repository.OrderChangeLogRepository;

import lombok.RequiredArgsConstructor;

/**
 * Read/append access to one order's change history (bdup-7) — extracted from
 * {@code OrderService} as a self-contained collaborator, called from it as a facade.
 * {@code OrderService} still owns the membership/order-existence checks; this class only
 * ever touches the {@code orderChangeLogs} collection.
 */
@Service
@RequiredArgsConstructor
class OrderChangeLogService {

    private final OrderChangeLogRepository repository;

    List<OrderChangeLog> history(String orderId) {
        return repository.findByOrderId(orderId).map(List::of).orElse(List.of());
    }

    void append(String orderId, Consumer<OrderChangeLog> mutator) {
        OrderChangeLog log = repository.findByOrderId(orderId)
                .orElseGet(() -> OrderChangeLog.builder().orderId(orderId).build());
        mutator.accept(log);
        repository.save(log);
    }
}
