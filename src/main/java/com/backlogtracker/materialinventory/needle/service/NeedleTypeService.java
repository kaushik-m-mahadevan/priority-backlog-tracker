package com.backlogtracker.materialinventory.needle.service;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.backlogtracker.commons.group.service.GroupService;
import com.backlogtracker.materialinventory.needle.domain.NeedleType;
import com.backlogtracker.materialinventory.needle.dto.CreateNeedleTypeRequest;
import com.backlogtracker.materialinventory.needle.dto.NeedleTypeView;
import com.backlogtracker.materialinventory.needle.repository.NeedleInventoryEntryRepository;
import com.backlogtracker.materialinventory.needle.repository.NeedleTypeRepository;

import lombok.RequiredArgsConstructor;

/**
 * Business-wide canonical hook/needle sizes — same "any member can create or edit" rule
 * as {@code YarnTypeService}, kept as a separate catalog since hooks/needles are reusable
 * tools rather than a consumable material (design decision: a separate section for
 * needles).
 */
@Service
@RequiredArgsConstructor
public class NeedleTypeService {

    private final NeedleTypeRepository repository;
    private final NeedleInventoryEntryRepository inventoryEntries;
    private final GroupService groupService;

    public List<NeedleTypeView> list(String groupId, String userId) {
        groupService.requireMember(groupId, userId);
        return repository.findByGroupId(groupId).stream().map(NeedleTypeView::of).toList();
    }

    public NeedleTypeView create(String groupId, String userId, CreateNeedleTypeRequest request) {
        groupService.requireMember(groupId, userId);
        if (request.kind() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "kind is required");
        }
        String size = requireText(request.size());

        repository.findByGroupIdAndKindAndSizeIgnoreCase(groupId, request.kind(), size).ifPresent(existing -> {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This kind/size combination already exists — use the existing needle type instead");
        });

        NeedleType saved = repository.save(NeedleType.builder()
                .groupId(groupId)
                .kind(request.kind())
                .size(size)
                .notes(trimOrNull(request.notes()))
                .build());
        return NeedleTypeView.of(saved);
    }

    public NeedleTypeView update(String groupId, String userId, String needleTypeId, CreateNeedleTypeRequest request) {
        groupService.requireMember(groupId, userId);
        NeedleType needleType = requireById(groupId, needleTypeId);
        if (request.kind() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "kind is required");
        }
        String size = requireText(request.size());

        repository.findByGroupIdAndKindAndSizeIgnoreCase(groupId, request.kind(), size)
                .filter(existing -> !existing.getId().equals(needleTypeId))
                .ifPresent(existing -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "This kind/size combination already exists — use the existing needle type instead");
                });

        needleType.setKind(request.kind());
        needleType.setSize(size);
        needleType.setNotes(trimOrNull(request.notes()));
        return NeedleTypeView.of(repository.save(needleType));
    }

    public void delete(String groupId, String userId, String needleTypeId) {
        groupService.requireMember(groupId, userId);
        requireById(groupId, needleTypeId);
        if (!inventoryEntries.findByGroupIdAndNeedleTypeId(groupId, needleTypeId).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This needle type is still in someone's inventory — remove those entries first");
        }
        repository.deleteById(needleTypeId);
    }

    public NeedleType requireById(String groupId, String needleTypeId) {
        NeedleType needleType = repository.findById(needleTypeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Needle type not found"));
        if (!groupId.equals(needleType.getGroupId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Needle type not found");
        }
        return needleType;
    }

    private static String requireText(String value) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "size is required");
        }
        return value.trim();
    }

    private static String trimOrNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
