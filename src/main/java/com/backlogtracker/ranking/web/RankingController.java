package com.backlogtracker.ranking.web;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.backlogtracker.group.service.GroupService;
import com.backlogtracker.ranking.RankingService;
import com.backlogtracker.ranking.dto.RankedItemView;
import com.backlogtracker.ranking.dto.TopListView;
import com.backlogtracker.security.AuthUser;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/items")
@RequiredArgsConstructor
public class RankingController {

    private final RankingService rankingService;
    private final GroupService groupService;

    /** Top N live items in {@code groupId} by sortScore (design §3). */
    @GetMapping("/top")
    public TopListView top(@RequestParam String groupId,
                           @RequestParam(defaultValue = "10") int limit,
                           @AuthenticationPrincipal AuthUser actor) {
        groupService.requireMember(groupId, actor.id());
        return rankingService.top(groupId, limit);
    }

    /** The N fastest items to knock out now in {@code groupId} — effort asc (design §23). */
    @GetMapping("/quick-wins")
    public List<RankedItemView> quickWins(@RequestParam String groupId,
                                          @RequestParam(defaultValue = "10") int limit,
                                          @AuthenticationPrincipal AuthUser actor) {
        groupService.requireMember(groupId, actor.id());
        return rankingService.quickWins(groupId, limit);
    }
}
