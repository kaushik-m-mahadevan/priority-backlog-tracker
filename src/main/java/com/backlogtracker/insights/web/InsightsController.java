package com.backlogtracker.insights.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.backlogtracker.insights.dto.NeedsAttentionView;
import com.backlogtracker.insights.dto.OwnerWorkloadView;
import com.backlogtracker.insights.service.AgingService;
import com.backlogtracker.insights.service.CompletionStatsService;
import com.backlogtracker.insights.service.CompletionStatsService.CompletionStats;
import com.backlogtracker.insights.service.WorkloadService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/insights")
@RequiredArgsConstructor
public class InsightsController {

    private final AgingService agingService;
    private final WorkloadService workloadService;
    private final CompletionStatsService completionStats;

    /** "Needs Attention" panel — stale &amp; overdue, buried low-priority (design §5). */
    @GetMapping("/needs-attention")
    public NeedsAttentionView needsAttention() {
        return agingService.needsAttention();
    }

    /** Owner Workload Overview (design §6). */
    @GetMapping("/workload")
    public OwnerWorkloadView.Overview workload() {
        return workloadService.overview();
    }

    /** How many items were finished in the last {@code days}, and when the last one was. */
    @GetMapping("/completions")
    public CompletionStats completions(@RequestParam(defaultValue = "30") int days) {
        return completionStats.recent(days);
    }
}
