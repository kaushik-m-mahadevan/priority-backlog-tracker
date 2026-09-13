package com.backlogtracker.ordertracker.order.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.stereotype.Component;

import com.backlogtracker.ordertracker.order.domain.Order;
import com.backlogtracker.ordertracker.order.domain.Packaging;
import com.backlogtracker.ordertracker.order.domain.Payment;
import com.backlogtracker.ordertracker.order.domain.PaymentStatus;
import com.backlogtracker.ordertracker.order.domain.StageProgress;

/**
 * Pure math for an individual order (design §5/§6/§10) — no I/O, fully unit-testable.
 */
@Component
public class OrderCalculator {

    /** Total estimated crafting time across all stages, hours. */
    public double totalEstimatedHours(List<StageProgress> stages) {
        return stages.stream().mapToDouble(StageProgress::getEstimatedHours).sum();
    }

    /**
     * Overall completion % = sum over stages of (stage's own completion fraction × that
     * stage's share of the order's total estimated hours) — the time-weighted formula
     * (platform integration decision), not a flat average or a fixed configured weight.
     * Zero-hour orders (no stages estimated yet) read as 0% rather than dividing by zero.
     */
    public double overallCompletionFraction(List<StageProgress> stages) {
        double totalHours = totalEstimatedHours(stages);
        if (totalHours <= 0) {
            return 0.0;
        }
        double weighted = 0.0;
        for (StageProgress s : stages) {
            weighted += s.getCompletionFraction() * (s.getEstimatedHours() / totalHours);
        }
        return weighted;
    }

    /** Materials + packaging, marked up by overhead then profit margin (design §5.10). */
    public double totalCost(double materialsCost, Packaging packaging, double overheadPct, double profitMarginPct) {
        double base = materialsCost + packaging.cost();
        double withOverhead = base * (1 + overheadPct);
        return withOverhead * (1 + profitMarginPct);
    }

    public double totalCost(Order order) {
        return totalCost(order.getMaterialsCost(), order.getPackaging(),
                order.getOverheadPercentage(), order.getProfitMarginPercentage());
    }

    /** created + (total estimated hours / primary creator's hours-per-day), rounded up to
     *  a whole day (design §5, one primary creator's capacity governs the whole order). */
    public Instant computedDueDate(Instant createdAt, List<StageProgress> stages, double packagingHours,
                                    double creatorHoursPerDay) {
        double hours = totalEstimatedHours(stages) + packagingHours;
        long days = Math.max(1, (long) Math.ceil(hours / creatorHoursPerDay));
        return createdAt.plus(days, ChronoUnit.DAYS);
    }

    /** Bulk orders use the max-across-every-assigned-creator offset (design §9), unlike
     *  individual orders' single-primary-creator rule — the batch isn't done until its
     *  slowest-loaded creator finishes their own split. */
    public Instant computedBulkDueDate(Instant createdAt, List<CreatorWorkload> workloads) {
        long maxDays = workloads.stream()
                .mapToLong(w -> Math.max(1, (long) Math.ceil(w.hours() / w.hoursPerDay())))
                .max().orElse(1);
        return createdAt.plus(maxDays, ChronoUnit.DAYS);
    }

    public record CreatorWorkload(double hours, double hoursPerDay) {
    }

    /**
     * Net paid = sum of payments (a refund is recorded as a negative-amount Payment).
     * {@link PaymentStatus#FULLY_REFUNDED} is distinguished from {@link PaymentStatus#UNPAID}
     * by checking whether any positive payment ever existed, even though both read as
     * {@code netPaid <= 0} under the spec's literal formula (platform integration decision).
     */
    public PaymentStatus derivePaymentStatus(List<Payment> payments, double totalCost) {
        double net = payments.stream().mapToDouble(Payment::amountValue).sum();
        boolean everHadPositivePayment = payments.stream().anyMatch(p -> p.amountValue() > 0);

        if (net >= totalCost && totalCost > 0) {
            return PaymentStatus.PAID;
        }
        if (net <= 0) {
            return everHadPositivePayment ? PaymentStatus.FULLY_REFUNDED : PaymentStatus.UNPAID;
        }
        return PaymentStatus.PARTIALLY_PAID;
    }
}
