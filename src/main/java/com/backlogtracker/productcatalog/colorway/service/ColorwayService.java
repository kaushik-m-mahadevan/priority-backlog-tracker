package com.backlogtracker.productcatalog.colorway.service;

import java.time.Instant;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import static com.backlogtracker.commons.web.RequiredField.requireText;
import static com.backlogtracker.commons.web.RequiredField.trimOrNull;

import com.backlogtracker.commons.group.service.GroupService;
import com.backlogtracker.commons.pattern.domain.Pattern;
import com.backlogtracker.commons.web.ScopedLookup;
import com.backlogtracker.productcatalog.colorway.domain.Colorway;
import com.backlogtracker.productcatalog.colorway.dto.ColorwayView;
import com.backlogtracker.productcatalog.colorway.dto.CreateColorwayRequest;
import com.backlogtracker.productcatalog.colorway.repository.ColorwayRepository;

import lombok.RequiredArgsConstructor;

/**
 * Business-wide catalog of colorways — any member can create or edit one (design
 * decision, mirroring YarnTypeService), from idea box through to promoted catalog entry.
 */
@Service
@RequiredArgsConstructor
public class ColorwayService {

    private final ColorwayRepository repository;
    private final GroupService groupService;

    public List<ColorwayView> list(String groupId, String userId) {
        groupService.requireMember(groupId, userId);
        return repository.findByGroupId(groupId).stream().map(ColorwayView::of).toList();
    }

    public ColorwayView create(String groupId, String userId, CreateColorwayRequest request) {
        groupService.requireMember(groupId, userId);
        String name = requireText(request.name(), "name");
        String colour = requireText(request.colour(), "colour");

        repository.findByGroupIdAndNameIgnoreCaseAndColourIgnoreCase(groupId, name, colour)
                .ifPresent(existing -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "This name/colour combination already exists — use the existing colorway instead");
                });

        Colorway saved = repository.save(Colorway.builder()
                .groupId(groupId)
                .name(name)
                .colour(colour)
                .pattern(toPattern(request.recipeSteps(), request.referenceLink()))
                .estimatedCost(request.estimatedCost())
                .notes(trimOrNull(request.notes()))
                .ideabox(true)
                .createdAt(Instant.now())
                .build());
        return ColorwayView.of(saved);
    }

    public ColorwayView update(String groupId, String userId, String colorwayId, CreateColorwayRequest request) {
        groupService.requireMember(groupId, userId);
        Colorway colorway = requireById(groupId, colorwayId);
        String name = requireText(request.name(), "name");
        String colour = requireText(request.colour(), "colour");

        repository.findByGroupIdAndNameIgnoreCaseAndColourIgnoreCase(groupId, name, colour)
                .filter(existing -> !existing.getId().equals(colorwayId))
                .ifPresent(existing -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "This name/colour combination already exists — use the existing colorway instead");
                });

        colorway.setName(name);
        colorway.setColour(colour);
        colorway.setPattern(toPattern(request.recipeSteps(), request.referenceLink()));
        colorway.setEstimatedCost(request.estimatedCost());
        colorway.setNotes(trimOrNull(request.notes()));
        return ColorwayView.of(repository.save(colorway));
    }

    /** Simple flag flip, no other side effects (design decision). */
    public ColorwayView promote(String groupId, String userId, String colorwayId) {
        groupService.requireMember(groupId, userId);
        Colorway colorway = requireById(groupId, colorwayId);
        colorway.setIdeabox(false);
        return ColorwayView.of(repository.save(colorway));
    }

    public void delete(String groupId, String userId, String colorwayId) {
        groupService.requireMember(groupId, userId);
        requireById(groupId, colorwayId);
        repository.deleteById(colorwayId);
    }

    private Colorway requireById(String groupId, String colorwayId) {
        return ScopedLookup.requireInGroup(repository.findById(colorwayId), Colorway::getGroupId, groupId, "Colorway not found");
    }

    private static Pattern toPattern(List<String> recipeSteps, String referenceLink) {
        String link = trimOrNull(referenceLink);
        if ((recipeSteps == null || recipeSteps.isEmpty()) && link == null) {
            return null;
        }
        return Pattern.builder().recipeSteps(recipeSteps == null ? List.of() : recipeSteps).referenceLink(link).build();
    }

}
