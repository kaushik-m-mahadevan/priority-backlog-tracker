package com.backlogtracker.ranking;

import java.util.List;

import org.springframework.stereotype.Service;

import com.backlogtracker.config.service.ConfigService;
import com.backlogtracker.item.service.ItemService;
import com.backlogtracker.ranking.dto.RankedItemView;

import lombok.RequiredArgsConstructor;

/** Assembles the ranked views for one group (design §3, §23). Membership checked upstream. */
@Service
@RequiredArgsConstructor
public class RankingService {

    public static final int DEFAULT_LIMIT = 10;

    private final ItemService itemService;
    private final ConfigService configService;
    private final ScoringService scoringService;

    /** Top {@code limit} live items in the group by sortScore (design §3). */
    public List<RankedItemView> top(String groupId, int limit) {
        return rank(groupId, ScoringService.ORDER, limit);
    }

    /** Fastest {@code limit} live items in the group — effort asc, sortScore desc (§23). */
    public List<RankedItemView> quickWins(String groupId, int limit) {
        return rank(groupId, ScoringService.QUICK_WINS_ORDER, limit);
    }

    private List<RankedItemView> rank(String groupId,
                                      java.util.Comparator<ScoringService.Scored> order, int limit) {
        var cfg = configService.getConfig();
        return scoringService.rankBy(itemService.listLive(groupId), cfg, order).stream()
                .limit(Math.max(0, limit))
                .map(RankedItemView::of)
                .toList();
    }
}
