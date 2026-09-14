package com.backlogtracker.ordertracker.master.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.backlogtracker.commons.group.domain.Group;
import com.backlogtracker.commons.group.service.GroupService;
import com.backlogtracker.ordertracker.master.domain.CostConfigChangeRequest;
import com.backlogtracker.ordertracker.master.domain.CostConfigChangeStatus;
import com.backlogtracker.ordertracker.master.repository.CostConfigChangeRequestRepository;

import lombok.RequiredArgsConstructor;

/**
 * Proposes and resolves changes to a business's overhead %/profit margin %. Every current
 * group member must approve before the change takes effect; a single rejection cancels the
 * whole proposal (mirrors Backlog Tracker's own unanimous archive-approval pattern, kept as
 * a separate implementation per the platform's no-cross-applet-imports rule).
 */
@Service
@RequiredArgsConstructor
public class CostConfigChangeService {

    private final CostConfigChangeRequestRepository repository;
    private final GroupService groupService;
    private final BusinessConfigService businessConfigService;
    private final Clock clock;

    public CostConfigChangeRequest propose(String groupId, String userId,
                                           double overheadPercentage, double profitMarginPercentage) {
        groupService.requireMember(groupId, userId);
        repository.findByGroupIdAndStatus(groupId, CostConfigChangeStatus.PENDING).ifPresent(existing -> {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A cost config change is already pending approval for this group");
        });
        CostConfigChangeRequest request = CostConfigChangeRequest.builder()
                .groupId(groupId)
                .proposedOverheadPercentage(overheadPercentage)
                .proposedProfitMarginPercentage(profitMarginPercentage)
                .proposedByUserId(userId)
                .status(CostConfigChangeStatus.PENDING)
                .createdAt(Instant.now(clock))
                .build();
        request.getApprovedByUserIds().add(userId);
        return repository.save(request);
    }

    public CostConfigChangeRequest approve(String groupId, String userId, String requestId) {
        Group group = groupService.requireMember(groupId, userId);
        CostConfigChangeRequest request = pendingRequest(groupId, requestId);
        if (!request.getApprovedByUserIds().contains(userId)) {
            request.getApprovedByUserIds().add(userId);
        }
        if (request.getApprovedByUserIds().containsAll(group.getMemberIds())) {
            businessConfigService.applyCostConfig(groupId, request.getProposedOverheadPercentage(),
                    request.getProposedProfitMarginPercentage());
            request.setStatus(CostConfigChangeStatus.APPROVED);
            request.setResolvedAt(Instant.now(clock));
        }
        return repository.save(request);
    }

    public CostConfigChangeRequest reject(String groupId, String userId, String requestId) {
        groupService.requireMember(groupId, userId);
        CostConfigChangeRequest request = pendingRequest(groupId, requestId);
        request.setStatus(CostConfigChangeStatus.REJECTED);
        request.setRejectedByUserId(userId);
        request.setResolvedAt(Instant.now(clock));
        return repository.save(request);
    }

    public List<CostConfigChangeRequest> list(String groupId, String userId) {
        groupService.requireMember(groupId, userId);
        return repository.findByGroupId(groupId);
    }

    private CostConfigChangeRequest pendingRequest(String groupId, String requestId) {
        CostConfigChangeRequest request = repository.findById(requestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Change request not found"));
        if (!request.getGroupId().equals(groupId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Change request not found");
        }
        if (request.getStatus() != CostConfigChangeStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This change request has already been resolved");
        }
        return request;
    }
}
