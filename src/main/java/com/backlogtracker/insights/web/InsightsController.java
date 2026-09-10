package com.backlogtracker.insights.web;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.backlogtracker.group.service.GroupService;
import com.backlogtracker.insights.dto.NeedsAttentionView;
import com.backlogtracker.insights.dto.OwnerWorkloadView;
import com.backlogtracker.insights.service.AgingService;
import com.backlogtracker.insights.service.CompletionStatsService;
import com.backlogtracker.insights.service.CompletionStatsService.CompletionStats;
import com.backlogtracker.insights.service.WorkloadService;
import com.backlogtracker.security.AuthUser;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/insights")
@RequiredArgsConstructor
public class InsightsController {

    private final AgingService agingService;
    private final WorkloadService workloadService;
    private final CompletionStatsService completionStats;
    private final GroupService groupService;

    /** "Needs Attention" for one group — stale &amp; overdue, buried low-priority (design §5). */
    @GetMapping("/needs-attention")
    public NeedsAttentionView needsAttention(@RequestParam String groupId,
                                             @AuthenticationPrincipal AuthUser actor) {
        groupService.requireMember(groupId, actor.id());
        return agingService.needsAttention(groupId);
    }

    /** Owner Workload for one group (design §6). */
    @GetMapping("/workload")
    public OwnerWorkloadView.Overview workload(@RequestParam String groupId,
                                               @AuthenticationPrincipal AuthUser actor) {
        groupService.requireMember(groupId, actor.id());
        return workloadService.overview(groupId);
    }

    /** How many items the group finished in the last {@code days}, and when the last was. */
    @GetMapping("/completions")
    public CompletionStats completions(@RequestParam String groupId,
                                       @RequestParam(defaultValue = "30") int days,
                                       @AuthenticationPrincipal AuthUser actor) {
        groupService.requireMember(groupId, actor.id());
        return completionStats.recent(groupId, days);
    }
}
