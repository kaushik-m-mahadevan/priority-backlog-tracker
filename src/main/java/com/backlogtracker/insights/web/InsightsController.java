package com.backlogtracker.insights.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.backlogtracker.insights.dto.NeedsAttentionView;
import com.backlogtracker.insights.dto.OwnerWorkloadView;
import com.backlogtracker.insights.service.AgingService;
import com.backlogtracker.insights.service.WorkloadService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/insights")
@RequiredArgsConstructor
public class InsightsController {

    private final AgingService agingService;
    private final WorkloadService workloadService;

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
}
