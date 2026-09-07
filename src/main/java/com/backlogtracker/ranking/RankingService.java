package com.backlogtracker.ranking;

import java.util.List;

import org.springframework.stereotype.Service;

import com.backlogtracker.config.service.ConfigService;
import com.backlogtracker.item.service.ItemService;
import com.backlogtracker.ranking.dto.RankedItemView;

import lombok.RequiredArgsConstructor;

/**
 * Assembles the ranked views (design §3). Currently shared scope only; personal scope
 * is wired in a later step and always computed separately (§13).
 */
@Service
@RequiredArgsConstructor
public class RankingService {

    public static final int DEFAULT_LIMIT = 10;

    private final ItemService itemService;
    private final ConfigService configService;
    private final ScoringService scoringService;

    /** Top {@code limit} live shared items by sortScore (design §3). */
    public List<RankedItemView> topShared(int limit) {
        var cfg = configService.getConfig();
        return scoringService.rank(itemService.listShared(), cfg).stream()
                .limit(Math.max(0, limit))
                .map(RankedItemView::of)
                .toList();
    }
}
