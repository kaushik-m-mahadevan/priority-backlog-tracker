package com.backlogtracker.ranking.web;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.backlogtracker.ranking.RankingService;
import com.backlogtracker.ranking.dto.RankedItemView;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/items")
@RequiredArgsConstructor
public class RankingController {

    private final RankingService rankingService;

    /** Top N live items by sortScore (design §3). Shared scope only for now. */
    @GetMapping("/top")
    public List<RankedItemView> top(
            @RequestParam(defaultValue = "shared") String scope,
            @RequestParam(defaultValue = "10") int limit) {
        requireShared(scope);
        return rankingService.topShared(limit);
    }

    static void requireShared(String scope) {
        if (!"shared".equalsIgnoreCase(scope)) {
            throw new IllegalArgumentException(
                    "Only scope=shared is supported yet; personal lists come in a later step");
        }
    }
}
