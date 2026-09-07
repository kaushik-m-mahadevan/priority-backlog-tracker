package com.backlogtracker.ranking.dto;

import com.backlogtracker.item.dto.ItemView;
import com.backlogtracker.ranking.ScoringService;

/** An {@link ItemView} plus its computed ranking score and factor breakdown (§3). */
public record RankedItemView(
        ItemView item,
        double priorityFactor,
        double urgencyFactor,
        double effortFactor,
        double sortScore) {

    public static RankedItemView of(ScoringService.Scored scored) {
        ScoringService.Score s = scored.score();
        return new RankedItemView(
                ItemView.of(scored.item()),
                round(s.priorityFactor()),
                round(s.urgencyFactor()),
                round(s.effortFactor()),
                round(s.sortScore()));
    }

    private static double round(double v) {
        return Math.round(v * 1_000_000.0) / 1_000_000.0;
    }
}
