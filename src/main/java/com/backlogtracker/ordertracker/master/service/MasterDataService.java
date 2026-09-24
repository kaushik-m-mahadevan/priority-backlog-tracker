package com.backlogtracker.ordertracker.master.service;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.backlogtracker.commons.group.service.GroupService;
import com.backlogtracker.commons.pattern.domain.Pattern;
import com.backlogtracker.ordertracker.master.domain.AssemblyPreset;
import com.backlogtracker.ordertracker.master.domain.ComponentTemplate;
import com.backlogtracker.ordertracker.master.domain.PresetOption;
import com.backlogtracker.ordertracker.master.domain.ShippingLanePreset;
import com.backlogtracker.ordertracker.master.repository.AssemblyPresetRepository;
import com.backlogtracker.ordertracker.master.repository.ComponentTemplateRepository;
import com.backlogtracker.ordertracker.master.repository.PresetOptionRepository;
import com.backlogtracker.ordertracker.master.repository.ShippingLanePresetRepository;

import lombok.RequiredArgsConstructor;

/** CRUD for the smaller per-group master lists (design §2) — packaging presets, shipping
 *  lanes, and component templates. Any member may manage these, same as Backlog Tracker's
 *  categories. */
@Service
@RequiredArgsConstructor
public class MasterDataService {

    private final PresetOptionRepository presetOptions;
    private final AssemblyPresetRepository assemblyPresets;
    private final ShippingLanePresetRepository shippingLanes;
    private final ComponentTemplateRepository componentTemplates;
    private final GroupService groupService;

    public List<PresetOption> packagingPresets(String groupId, String userId) {
        groupService.requireMember(groupId, userId);
        return presetOptions.findByGroupId(groupId);
    }

    public PresetOption addPackagingPreset(String groupId, String userId, String label,
                                           double estimatedCost, double estimatedTimeHours) {
        groupService.requireMember(groupId, userId);
        return presetOptions.save(PresetOption.builder()
                .groupId(groupId).label(label)
                .estimatedCost(estimatedCost).estimatedTimeHours(estimatedTimeHours)
                .build());
    }

    /** Used by Order Tracker's order-creation flow to snapshot a preset's cost/time onto
     *  the order at creation, so a later preset edit never retroactively changes past
     *  orders. */
    public PresetOption requirePackagingPreset(String groupId, String userId, String presetId) {
        groupService.requireMember(groupId, userId);
        PresetOption p = presetOptions.findById(presetId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Preset not found"));
        requireOwnedByGroup(p.getGroupId(), groupId);
        return p;
    }

    public void removePackagingPreset(String groupId, String userId, String presetId) {
        groupService.requireMember(groupId, userId);
        PresetOption p = presetOptions.findById(presetId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Preset not found"));
        requireOwnedByGroup(p.getGroupId(), groupId);
        presetOptions.deleteById(presetId);
    }

    public List<AssemblyPreset> assemblyPresets(String groupId, String userId) {
        groupService.requireMember(groupId, userId);
        return assemblyPresets.findByGroupId(groupId);
    }

    public AssemblyPreset addAssemblyPreset(String groupId, String userId, String label,
                                            double estimatedCost, double estimatedTimeHours) {
        groupService.requireMember(groupId, userId);
        return assemblyPresets.save(AssemblyPreset.builder()
                .groupId(groupId).label(label)
                .estimatedCost(estimatedCost).estimatedTimeHours(estimatedTimeHours)
                .build());
    }

    /** Same snapshot-at-creation contract as {@link #requirePackagingPreset}. */
    public AssemblyPreset requireAssemblyPreset(String groupId, String userId, String presetId) {
        groupService.requireMember(groupId, userId);
        AssemblyPreset p = assemblyPresets.findById(presetId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Preset not found"));
        requireOwnedByGroup(p.getGroupId(), groupId);
        return p;
    }

    public void removeAssemblyPreset(String groupId, String userId, String presetId) {
        groupService.requireMember(groupId, userId);
        AssemblyPreset p = assemblyPresets.findById(presetId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Preset not found"));
        requireOwnedByGroup(p.getGroupId(), groupId);
        assemblyPresets.deleteById(presetId);
    }

    public List<ShippingLanePreset> shippingLanes(String groupId, String userId) {
        groupService.requireMember(groupId, userId);
        return shippingLanes.findByGroupId(groupId);
    }

    public ShippingLanePreset addShippingLane(String groupId, String userId, String originLocationCode,
                                              String destinationLocationCode, double estimatedCost,
                                              double estimatedTimeHours, String note) {
        groupService.requireMember(groupId, userId);
        return shippingLanes.save(ShippingLanePreset.builder()
                .groupId(groupId)
                .originLocationCode(originLocationCode)
                .destinationLocationCode(destinationLocationCode)
                .estimatedCost(estimatedCost)
                .estimatedTimeHours(estimatedTimeHours)
                .note(note)
                .build());
    }

    public void removeShippingLane(String groupId, String userId, String laneId) {
        groupService.requireMember(groupId, userId);
        ShippingLanePreset lane = shippingLanes.findById(laneId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Lane not found"));
        requireOwnedByGroup(lane.getGroupId(), groupId);
        shippingLanes.deleteById(laneId);
    }

    public List<ComponentTemplate> componentTemplates(String groupId, String userId) {
        groupService.requireMember(groupId, userId);
        return componentTemplates.findByGroupId(groupId);
    }

    public ComponentTemplate addComponentTemplate(String groupId, String userId, String label, Pattern pattern,
                                                   double baseCraftingTimeHours, String notes) {
        groupService.requireMember(groupId, userId);
        return componentTemplates.save(ComponentTemplate.builder()
                .groupId(groupId).label(label).pattern(pattern)
                .baseCraftingTimeHours(baseCraftingTimeHours).notes(notes)
                .build());
    }

    public ComponentTemplate updateComponentTemplate(String groupId, String userId, String templateId, String label,
                                                      Pattern pattern, double baseCraftingTimeHours, String notes) {
        groupService.requireMember(groupId, userId);
        ComponentTemplate t = requireComponentTemplate(groupId, userId, templateId);
        t.setLabel(label);
        t.setPattern(pattern);
        t.setBaseCraftingTimeHours(baseCraftingTimeHours);
        t.setNotes(notes);
        return componentTemplates.save(t);
    }

    /** Used by Order Tracker's order-creation flow to snapshot a template's crafting time
     *  onto the order's component at creation, so a later template edit never
     *  retroactively changes a past order — same convention as {@link #requirePackagingPreset}. */
    public ComponentTemplate requireComponentTemplate(String groupId, String userId, String templateId) {
        groupService.requireMember(groupId, userId);
        ComponentTemplate t = componentTemplates.findById(templateId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Component template not found"));
        requireOwnedByGroup(t.getGroupId(), groupId);
        return t;
    }

    public void removeComponentTemplate(String groupId, String userId, String templateId) {
        groupService.requireMember(groupId, userId);
        ComponentTemplate t = requireComponentTemplate(groupId, userId, templateId);
        componentTemplates.deleteById(t.getId());
    }

    private static void requireOwnedByGroup(String actualGroupId, String expectedGroupId) {
        if (!expectedGroupId.equals(actualGroupId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Not found in this group");
        }
    }
}
