package com.backlogtracker.materialinventory.needle.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.backlogtracker.commons.group.service.GroupService;
import com.backlogtracker.materialinventory.needle.domain.NeedleInventoryEntry;
import com.backlogtracker.materialinventory.needle.dto.NeedleInventoryEntryView;
import com.backlogtracker.materialinventory.needle.repository.NeedleInventoryEntryRepository;

import lombok.RequiredArgsConstructor;

/**
 * Each member's own on-hand hook/needle counts — whole units, set directly (an upsert),
 * same visibility rule as yarn's {@code InventoryService} (business-wide read, self-only
 * write).
 */
@Service
@RequiredArgsConstructor
public class NeedleInventoryService {

    private final NeedleInventoryEntryRepository repository;
    private final NeedleTypeService needleTypeService;
    private final GroupService groupService;
    private final Clock clock;

    public List<NeedleInventoryEntryView> listAll(String groupId, String userId) {
        groupService.requireMember(groupId, userId);
        return repository.findByGroupId(groupId).stream().map(NeedleInventoryEntryView::of).toList();
    }

    public List<NeedleInventoryEntryView> mine(String groupId, String userId) {
        groupService.requireMember(groupId, userId);
        return repository.findByGroupIdAndUserId(groupId, userId).stream().map(NeedleInventoryEntryView::of).toList();
    }

    /** Setting quantity to exactly 0 removes the row, same as yarn's own inventory. */
    public void setMyQuantity(String groupId, String userId, String needleTypeId, int quantity) {
        groupService.requireMember(groupId, userId);
        needleTypeService.requireById(groupId, needleTypeId);
        if (quantity < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "quantity must not be negative");
        }

        NeedleInventoryEntry existing = repository.findByGroupIdAndUserIdAndNeedleTypeId(groupId, userId, needleTypeId).orElse(null);
        if (quantity == 0) {
            if (existing != null) {
                repository.deleteById(existing.getId());
            }
            return;
        }
        if (existing != null) {
            existing.setQuantity(quantity);
            existing.setUpdatedAt(Instant.now(clock));
            repository.save(existing);
            return;
        }
        repository.save(NeedleInventoryEntry.builder()
                .groupId(groupId)
                .userId(userId)
                .needleTypeId(needleTypeId)
                .quantity(quantity)
                .updatedAt(Instant.now(clock))
                .build());
    }
}
