package com.backlogtracker.backlogtracker.ranking;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.backlogtracker.backlogtracker.config.service.ConfigService;
import com.backlogtracker.backlogtracker.item.service.ItemService;
import com.backlogtracker.backlogtracker.ranking.dto.RankedItemView;
import com.backlogtracker.backlogtracker.ranking.dto.TopListView;

import lombok.RequiredArgsConstructor;

/** Assembles the ranked views for one group (design §3, §23). Membership checked upstream. */
@Service
@RequiredArgsConstructor
public class RankingService {

    public static final int DEFAULT_LIMIT = 10;

    private final ItemService itemService;
    private final ConfigService configService;
    private final ScoringService scoringService;

    /**
     * The Pecking Order: pinned items first (in score order), then the rest fill the
     * remaining slots up to {@code limit} (design §3 + pinning).
     */
    public TopListView top(String groupId, int limit) {
        var cfg = configService.getConfig();
        var ranked = scoringService.rankBy(itemService.listLive(groupId), cfg, ScoringService.ORDER);
        int lim = Math.max(0, limit);

        List<ScoringService.Scored> pinned = ranked.stream()
                .filter(s -> s.item().isPinned()).toList();
        List<ScoringService.Scored> rest = ranked.stream()
                .filter(s -> !s.item().isPinned()).toList();

        List<ScoringService.Scored> combined = new ArrayList<>(pinned.stream().limit(lim).toList());
        if (combined.size() < lim) {
            combined.addAll(rest.stream().limit(lim - combined.size()).toList());
        }
        return new TopListView(
                combined.stream().map(RankedItemView::of).toList(),
                pinned.size(),
                pinned.size() > lim);
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
