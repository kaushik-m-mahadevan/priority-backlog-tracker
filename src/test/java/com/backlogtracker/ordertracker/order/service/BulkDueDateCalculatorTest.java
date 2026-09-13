package com.backlogtracker.ordertracker.order.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.backlogtracker.ordertracker.order.service.OrderCalculator.CreatorWorkload;

class BulkDueDateCalculatorTest {

    private final OrderCalculator calc = new OrderCalculator();

    @Test
    void bulkDueDateIsDrivenByTheSlowestLoadedCreatorNotAnAverage() {
        Instant created = Instant.parse("2026-01-01T00:00:00Z");
        // creator A: 20 units * 1h = 20h / 4h-per-day = 5 days
        // creator B: 5 units * 1h = 5h / 8h-per-day = 1 day (rounds up)
        List<CreatorWorkload> workloads = List.of(
                new CreatorWorkload(20, 4),
                new CreatorWorkload(5, 8));
        Instant due = calc.computedBulkDueDate(created, workloads);
        assertThat(due).isEqualTo(created.plusSeconds(5 * 24 * 3600));
    }

    @Test
    void bulkDueDateNeverGoesBelowOneDayPerCreator() {
        Instant created = Instant.parse("2026-01-01T00:00:00Z");
        Instant due = calc.computedBulkDueDate(created, List.of(new CreatorWorkload(0.1, 8)));
        assertThat(due).isEqualTo(created.plusSeconds(24 * 3600));
    }
}
