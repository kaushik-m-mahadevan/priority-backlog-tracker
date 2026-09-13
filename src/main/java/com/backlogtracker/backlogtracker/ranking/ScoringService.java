package com.backlogtracker.backlogtracker.ranking;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;

import com.backlogtracker.backlogtracker.config.domain.AppConfig;
import com.backlogtracker.backlogtracker.item.domain.Item;

import lombok.RequiredArgsConstructor;

/**
 * Pure ranking math from design §3. No persistence — takes an {@link Item} and the
 * {@link AppConfig} and produces a {@code sortScore} plus its factor breakdown.
 *
 * <pre>
 * priorityFactor = priorityValues[priority] / max(priorityValues)
 * urgencyFactor  = 1 - min(max(daysRemaining,0), W) / W      where W = urgencyWindowDays*3
 * effortFactor   = 1 - min(effortMinutes, cap) / cap         where cap = effortCapDays*24*60
 * sortScore      = pW*priorityFactor + uW*urgencyFactor + eW*effortFactor
 * </pre>
 *
 * Tie-break (§3 revised): sortScore desc, then effortMinutes asc, then createdAt asc.
 */
@Service
@RequiredArgsConstructor
public class ScoringService {

    private final Clock clock;

    public record Score(double priorityFactor, double urgencyFactor,
                        double effortFactor, double sortScore) {
    }

    public record Scored(Item item, Score score) {
    }

    /** Top 10 / main ranking: sortScore desc, then effortMinutes asc, then createdAt asc (§3). */
    public static final Comparator<Scored> ORDER =
            Comparator.comparingDouble((Scored s) -> s.score().sortScore()).reversed()
                    .thenComparingLong(s -> effortMinutes(s.item()))
                    .thenComparing(s -> s.item().getCreatedAt(),
                            Comparator.nullsLast(Comparator.naturalOrder()));

    /** Quick Wins: effortMinutes asc, then sortScore desc as tiebreak (design §23). */
    public static final Comparator<Scored> QUICK_WINS_ORDER =
            Comparator.comparingLong((Scored s) -> effortMinutes(s.item()))
                    .thenComparing(Comparator.comparingDouble(
                            (Scored s) -> s.score().sortScore()).reversed())
                    .thenComparing(s -> s.item().getCreatedAt(),
                            Comparator.nullsLast(Comparator.naturalOrder()));

    public Score score(Item item, AppConfig cfg) {
        double priorityFactor = priorityFactor(item, cfg);
        double urgencyFactor = urgencyFactor(item, cfg);
        double effortFactor = effortFactor(item, cfg);
        double sortScore = cfg.getPriorityWeight() * priorityFactor
                + cfg.getUrgencyWeight() * urgencyFactor
                + cfg.getEffortWeight() * effortFactor;
        return new Score(priorityFactor, urgencyFactor, effortFactor, sortScore);
    }

    /** Scores every item and returns them ordered by the given comparator. */
    public List<Scored> rankBy(List<Item> items, AppConfig cfg, Comparator<Scored> order) {
        return items.stream()
                .map(i -> new Scored(i, score(i, cfg)))
                .sorted(order)
                .toList();
    }

    /** Scores every item and returns them ranked best-first (§3 ORDER). */
    public List<Scored> rank(List<Item> items, AppConfig cfg) {
        return rankBy(items, cfg, ORDER);
    }

    // --- factors (package-private for focused unit tests) ------------------------------

    double priorityFactor(Item item, AppConfig cfg) {
        int value = cfg.getPriorityValues().getOrDefault(item.getPriority(), 0);
        int max = cfg.getPriorityValues().values().stream().mapToInt(Integer::intValue).max().orElse(1);
        return max == 0 ? 0.0 : (double) value / max;
    }

    double urgencyFactor(Item item, AppConfig cfg) {
        double window = Math.max(1, cfg.getUrgencyWindowDays() * 3.0);
        long daysRemaining = daysRemaining(item);
        double clamped = Math.max(daysRemaining, 0);
        return 1.0 - Math.min(clamped, window) / window;
    }

    double effortFactor(Item item, AppConfig cfg) {
        double cap = Math.max(1, cfg.getEffortCapDays() * 24.0 * 60.0);
        double minutes = effortMinutes(item);
        return 1.0 - Math.min(minutes, cap) / cap;
    }

    long daysRemaining(Item item) {
        LocalDate today = LocalDate.now(clock);
        LocalDate due = item.getDueDate().atZone(ZoneOffset.UTC).toLocalDate();
        return ChronoUnit.DAYS.between(today, due);
    }

    private static long effortMinutes(Item item) {
        return item.getEffortEstimate() == null ? Long.MAX_VALUE : item.getEffortEstimate().toMinutes();
    }
}
