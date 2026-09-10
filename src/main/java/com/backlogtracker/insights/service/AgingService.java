package com.backlogtracker.insights.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;

import com.backlogtracker.config.domain.AppConfig;
import com.backlogtracker.config.service.ConfigService;
import com.backlogtracker.insights.dto.NeedsAttentionView;
import com.backlogtracker.insights.dto.NeedsAttentionView.FlaggedItem;
import com.backlogtracker.item.domain.Item;
import com.backlogtracker.item.domain.ItemStatus;
import com.backlogtracker.item.dto.ItemView;
import com.backlogtracker.item.service.ItemService;

import lombok.RequiredArgsConstructor;

/**
 * Aging &amp; neglect detection — the "Needs Attention" panel (design §5).
 *
 * <ul>
 *   <li><b>Stale &amp; Overdue:</b> {@code today - dueDate > staleThresholdDays},
 *       status Backlog or In Progress.</li>
 *   <li><b>Buried Low-Priority:</b> {@code today - createdAt > buriedThresholdDays},
 *       priority in {@code buriedPriorityLevels}, status Backlog.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class AgingService {

    private final ItemService itemService;
    private final ConfigService configService;
    private final Clock clock;

    public NeedsAttentionView needsAttention(String groupId) {
        AppConfig cfg = configService.getConfig();
        LocalDate today = LocalDate.now(clock);
        List<Item> live = itemService.listLive(groupId);

        List<FlaggedItem> stale = live.stream()
                .filter(i -> i.getDueDate() != null)
                .map(i -> new FlaggedItem(ItemView.of(i), daysBetween(dateOf(i.getDueDate()), today)))
                .filter(f -> f.days() > cfg.getStaleThresholdDays())
                .sorted(Comparator.comparingLong(FlaggedItem::days).reversed())
                .toList();

        List<FlaggedItem> buried = live.stream()
                .filter(i -> i.getStatus() == ItemStatus.BACKLOG)
                .filter(i -> cfg.getBuriedPriorityLevels().contains(i.getPriority()))
                .filter(i -> i.getCreatedAt() != null)
                .map(i -> new FlaggedItem(ItemView.of(i), daysBetween(dateOf(i.getCreatedAt()), today)))
                .filter(f -> f.days() > cfg.getBuriedThresholdDays())
                .sorted(Comparator.comparingLong(FlaggedItem::days).reversed())
                .toList();

        return new NeedsAttentionView(stale, buried);
    }

    /**
     * Grove health for the opt-in "the tree suffers" animation. {@code neglect} = live
     * items that are overdue OR untouched for longer than the stale threshold; the stage
     * (0 healthy → 4 stump) drives how far the tree degrades.
     */
    public GroveHealth health(String groupId) {
        AppConfig cfg = configService.getConfig();
        LocalDate today = LocalDate.now(clock);
        Instant now = clock.instant();
        List<Item> live = itemService.listLive(groupId);

        long overdue = live.stream()
                .filter(i -> i.getDueDate() != null && i.getDueDate().isBefore(now))
                .count();
        long stale = live.stream()
                .filter(i -> i.getUpdatedAt() != null)
                .filter(i -> daysBetween(dateOf(i.getUpdatedAt()), today) > cfg.getStaleThresholdDays())
                .count();
        long neglect = live.stream().filter(i -> {
            boolean o = i.getDueDate() != null && i.getDueDate().isBefore(now);
            boolean s = i.getUpdatedAt() != null
                    && daysBetween(dateOf(i.getUpdatedAt()), today) > cfg.getStaleThresholdDays();
            return o || s;
        }).count();

        int stage = neglect >= 10 ? 4 : neglect >= 6 ? 3 : neglect >= 3 ? 2 : neglect >= 1 ? 1 : 0;
        return new GroveHealth(overdue, stale, neglect, stage);
    }

    public record GroveHealth(long overdue, long stale, long neglect, int stage) {
    }

    private static LocalDate dateOf(java.time.Instant instant) {
        return instant.atZone(ZoneOffset.UTC).toLocalDate();
    }

    private static long daysBetween(LocalDate from, LocalDate to) {
        return ChronoUnit.DAYS.between(from, to);
    }
}
