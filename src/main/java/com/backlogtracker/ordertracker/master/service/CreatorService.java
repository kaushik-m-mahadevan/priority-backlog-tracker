package com.backlogtracker.ordertracker.master.service;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.backlogtracker.commons.group.service.GroupService;
import com.backlogtracker.ordertracker.OrderCodeWidths;
import com.backlogtracker.ordertracker.master.domain.Creator;
import com.backlogtracker.ordertracker.master.domain.LocationCode;
import com.backlogtracker.ordertracker.master.repository.CreatorRepository;

import lombok.RequiredArgsConstructor;

/**
 * Self-service Creator profile: a group member sets their own baseLocation and
 * hoursAvailablePerDay for that group's business (no admin-entry step — anyone who's a
 * member can become a Creator for it). creatorCode is assigned once, on first save, and
 * never changes after.
 */
@Service
@RequiredArgsConstructor
public class CreatorService {

    private final CreatorRepository repository;
    private final LocationCodeService locationCodeService;
    private final RandomCodeAssigner codeAssigner;
    private final GroupService groupService;

    public Creator myProfile(String groupId, String userId) {
        return repository.findByGroupIdAndUserId(groupId, userId).orElse(null);
    }

    public List<Creator> all(String groupId, String userId) {
        groupService.requireMember(groupId, userId);
        return repository.findByGroupId(groupId);
    }

    public Creator upsertMyProfile(String groupId, String userId, String name,
                                   String baseLocation, double hoursAvailablePerDay) {
        groupService.requireMember(groupId, userId);
        if (hoursAvailablePerDay <= 0) {
            throw new IllegalArgumentException("hoursAvailablePerDay must be positive");
        }
        LocationCode location = locationCodeService.getOrCreate(groupId, baseLocation.trim());

        Creator existing = repository.findByGroupIdAndUserId(groupId, userId).orElse(null);
        if (existing != null) {
            existing.setName(name);
            existing.setBaseLocation(location.getLocationName());
            existing.setLocationCode(location.getCode());
            existing.setHoursAvailablePerDay(hoursAvailablePerDay);
            return repository.save(existing);
        }

        return codeAssigner.assign(OrderCodeWidths.CREATOR_CODE_DIGITS, code -> repository.save(Creator.builder()
                .groupId(groupId)
                .userId(userId)
                .name(name)
                .baseLocation(location.getLocationName())
                .locationCode(location.getCode())
                .creatorCode(code)
                .hoursAvailablePerDay(hoursAvailablePerDay)
                .build()));
    }

    /** The caller's Creator profile for this group, or a clear 400 if they haven't set
     *  one up yet — surfaced wherever an order needs to assign/estimate against them. */
    public Creator require(String groupId, String userId) {
        return repository.findByGroupIdAndUserId(groupId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Set up your Order Tracker profile for this group first (base location + hours/day)"));
    }

    /** ad-2: self-service toggle, same "only the caller may change their own" shape as
     *  {@link #upsertMyProfile}. */
    public Creator setAutoSyncInventory(String groupId, String userId, boolean autoSyncInventory) {
        Creator creator = require(groupId, userId);
        creator.setAutoSyncInventory(autoSyncInventory);
        return repository.save(creator);
    }

    public Creator requireById(String groupId, String creatorId) {
        Creator c = repository.findById(creatorId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Creator not found"));
        if (!groupId.equals(c.getGroupId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Creator not found");
        }
        return c;
    }
}
