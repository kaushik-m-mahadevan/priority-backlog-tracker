package com.backlogtracker.materialinventory.yarn.service;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.backlogtracker.commons.group.service.GroupService;
import com.backlogtracker.materialinventory.inventory.repository.InventoryEntryRepository;
import com.backlogtracker.materialinventory.yarn.domain.YarnType;
import com.backlogtracker.materialinventory.yarn.dto.CreateYarnTypeRequest;
import com.backlogtracker.materialinventory.yarn.dto.YarnTypeView;
import com.backlogtracker.materialinventory.yarn.repository.YarnTypeRepository;

import lombok.RequiredArgsConstructor;

/**
 * Business-wide canonical yarn variants — any member can create or edit one (design
 * decision), since the point is a shared identity everyone's personal inventory can point
 * to, not a per-person catalog.
 */
@Service
@RequiredArgsConstructor
public class YarnTypeService {

    private final YarnTypeRepository repository;
    private final InventoryEntryRepository inventoryEntries;
    private final GroupService groupService;

    public List<YarnTypeView> list(String groupId, String userId) {
        groupService.requireMember(groupId, userId);
        return repository.findByGroupId(groupId).stream().map(YarnTypeView::of).toList();
    }

    public YarnTypeView create(String groupId, String userId, CreateYarnTypeRequest request) {
        groupService.requireMember(groupId, userId);
        String brand = requireText(request.brand(), "brand");
        String thickness = requireText(request.thickness(), "thickness");
        String colour = requireText(request.colour(), "colour");

        repository.findByGroupIdAndBrandIgnoreCaseAndThicknessIgnoreCaseAndColourIgnoreCase(groupId, brand, thickness, colour)
                .ifPresent(existing -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "This brand/thickness/colour combination already exists — use the existing yarn type instead");
                });

        YarnType saved = repository.save(YarnType.builder()
                .groupId(groupId)
                .brand(brand)
                .thickness(thickness)
                .colour(colour)
                .material(trimOrNull(request.material()))
                .skeinWeightGrams(request.skeinWeightGrams())
                .skeinLengthMeters(request.skeinLengthMeters())
                .recommendedHookSize(trimOrNull(request.recommendedHookSize()))
                .notes(trimOrNull(request.notes()))
                .build());
        return YarnTypeView.of(saved);
    }

    public YarnTypeView update(String groupId, String userId, String yarnTypeId, CreateYarnTypeRequest request) {
        groupService.requireMember(groupId, userId);
        YarnType yarnType = requireById(groupId, yarnTypeId);
        String brand = requireText(request.brand(), "brand");
        String thickness = requireText(request.thickness(), "thickness");
        String colour = requireText(request.colour(), "colour");

        repository.findByGroupIdAndBrandIgnoreCaseAndThicknessIgnoreCaseAndColourIgnoreCase(groupId, brand, thickness, colour)
                .filter(existing -> !existing.getId().equals(yarnTypeId))
                .ifPresent(existing -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "This brand/thickness/colour combination already exists — use the existing yarn type instead");
                });

        yarnType.setBrand(brand);
        yarnType.setThickness(thickness);
        yarnType.setColour(colour);
        yarnType.setMaterial(trimOrNull(request.material()));
        yarnType.setSkeinWeightGrams(request.skeinWeightGrams());
        yarnType.setSkeinLengthMeters(request.skeinLengthMeters());
        yarnType.setRecommendedHookSize(trimOrNull(request.recommendedHookSize()));
        yarnType.setNotes(trimOrNull(request.notes()));
        return YarnTypeView.of(repository.save(yarnType));
    }

    public void delete(String groupId, String userId, String yarnTypeId) {
        groupService.requireMember(groupId, userId);
        requireById(groupId, yarnTypeId);
        if (!inventoryEntries.findByGroupIdAndYarnTypeId(groupId, yarnTypeId).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This yarn type is still in someone's inventory — remove those entries first");
        }
        repository.deleteById(yarnTypeId);
    }

    public YarnType requireById(String groupId, String yarnTypeId) {
        YarnType yarnType = repository.findById(yarnTypeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Yarn type not found"));
        if (!groupId.equals(yarnType.getGroupId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Yarn type not found");
        }
        return yarnType;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " is required");
        }
        return value.trim();
    }

    private static String trimOrNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
