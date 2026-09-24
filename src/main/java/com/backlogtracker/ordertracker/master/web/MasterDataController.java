package com.backlogtracker.ordertracker.master.web;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.backlogtracker.commons.pattern.domain.Pattern;
import com.backlogtracker.commons.pattern.domain.PatternType;
import com.backlogtracker.commons.security.AuthUser;
import com.backlogtracker.commons.security.RequiresUser;
import com.backlogtracker.ordertracker.master.domain.AssemblyPreset;
import com.backlogtracker.ordertracker.master.domain.ComponentTemplate;
import com.backlogtracker.ordertracker.master.domain.PresetOption;
import com.backlogtracker.ordertracker.master.domain.ShippingLanePreset;
import com.backlogtracker.ordertracker.master.service.MasterDataService;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.PutMapping;

@RestController
@RequestMapping("/api/ordertracker/groups/{groupId}")
@RequiresUser
@RequiredArgsConstructor
public class MasterDataController {

    private final MasterDataService service;

    @GetMapping("/packaging-presets")
    public List<PresetOption> packagingPresets(@PathVariable String groupId, @AuthenticationPrincipal AuthUser actor) {
        return service.packagingPresets(groupId, actor.id());
    }

    @PostMapping("/packaging-presets")
    @ResponseStatus(HttpStatus.CREATED)
    public PresetOption addPackagingPreset(@PathVariable String groupId, @RequestBody PresetRequest request,
                                           @AuthenticationPrincipal AuthUser actor) {
        return service.addPackagingPreset(groupId, actor.id(), request.label(),
                request.estimatedCost(), request.estimatedTimeHours());
    }

    @DeleteMapping("/packaging-presets/{presetId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removePackagingPreset(@PathVariable String groupId, @PathVariable String presetId,
                                      @AuthenticationPrincipal AuthUser actor) {
        service.removePackagingPreset(groupId, actor.id(), presetId);
    }

    @GetMapping("/assembly-presets")
    public List<AssemblyPreset> assemblyPresets(@PathVariable String groupId, @AuthenticationPrincipal AuthUser actor) {
        return service.assemblyPresets(groupId, actor.id());
    }

    @PostMapping("/assembly-presets")
    @ResponseStatus(HttpStatus.CREATED)
    public AssemblyPreset addAssemblyPreset(@PathVariable String groupId, @RequestBody PresetRequest request,
                                            @AuthenticationPrincipal AuthUser actor) {
        return service.addAssemblyPreset(groupId, actor.id(), request.label(),
                request.estimatedCost(), request.estimatedTimeHours());
    }

    @DeleteMapping("/assembly-presets/{presetId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeAssemblyPreset(@PathVariable String groupId, @PathVariable String presetId,
                                     @AuthenticationPrincipal AuthUser actor) {
        service.removeAssemblyPreset(groupId, actor.id(), presetId);
    }

    @GetMapping("/shipping-lanes")
    public List<ShippingLanePreset> shippingLanes(@PathVariable String groupId, @AuthenticationPrincipal AuthUser actor) {
        return service.shippingLanes(groupId, actor.id());
    }

    @PostMapping("/shipping-lanes")
    @ResponseStatus(HttpStatus.CREATED)
    public ShippingLanePreset addShippingLane(@PathVariable String groupId, @RequestBody LaneRequest request,
                                              @AuthenticationPrincipal AuthUser actor) {
        return service.addShippingLane(groupId, actor.id(), request.originLocationCode(),
                request.destinationLocationCode(), request.estimatedCost(), request.estimatedTimeHours(),
                request.note());
    }

    @DeleteMapping("/shipping-lanes/{laneId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeShippingLane(@PathVariable String groupId, @PathVariable String laneId,
                                   @AuthenticationPrincipal AuthUser actor) {
        service.removeShippingLane(groupId, actor.id(), laneId);
    }

    @GetMapping("/component-templates")
    public List<ComponentTemplate> componentTemplates(@PathVariable String groupId, @AuthenticationPrincipal AuthUser actor) {
        return service.componentTemplates(groupId, actor.id());
    }

    @PostMapping("/component-templates")
    @ResponseStatus(HttpStatus.CREATED)
    public ComponentTemplate addComponentTemplate(@PathVariable String groupId, @RequestBody ComponentTemplateRequest request,
                                                  @AuthenticationPrincipal AuthUser actor) {
        return service.addComponentTemplate(groupId, actor.id(), request.label(), toPattern(request.pattern()),
                request.baseCraftingTimeHours(), request.notes());
    }

    @PutMapping("/component-templates/{templateId}")
    public ComponentTemplate updateComponentTemplate(@PathVariable String groupId, @PathVariable String templateId,
                                                      @RequestBody ComponentTemplateRequest request,
                                                      @AuthenticationPrincipal AuthUser actor) {
        return service.updateComponentTemplate(groupId, actor.id(), templateId, request.label(), toPattern(request.pattern()),
                request.baseCraftingTimeHours(), request.notes());
    }

    @DeleteMapping("/component-templates/{templateId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeComponentTemplate(@PathVariable String groupId, @PathVariable String templateId,
                                        @AuthenticationPrincipal AuthUser actor) {
        service.removeComponentTemplate(groupId, actor.id(), templateId);
    }

    private static Pattern toPattern(ComponentTemplateRequest.PatternInput input) {
        if (input == null) {
            return null;
        }
        return Pattern.builder().patternType(input.patternType()).templateName(input.templateName())
                .customPatternNotes(input.customPatternNotes())
                .attachmentUrls(input.attachmentUrls() == null ? List.of() : input.attachmentUrls())
                .recipeSteps(input.recipeSteps() == null ? List.of() : input.recipeSteps())
                .build();
    }

    public record PresetRequest(String label, double estimatedCost, double estimatedTimeHours) {
    }

    public record LaneRequest(String originLocationCode, String destinationLocationCode,
                              double estimatedCost, double estimatedTimeHours, String note) {
    }

    public record ComponentTemplateRequest(String label, PatternInput pattern, double baseCraftingTimeHours, String notes) {
        public record PatternInput(PatternType patternType, String templateName, String customPatternNotes,
                                   List<String> attachmentUrls, List<String> recipeSteps) {
        }
    }
}
