package com.backlogtracker.productcatalog.colorway.web;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.backlogtracker.commons.security.AuthUser;
import com.backlogtracker.commons.security.RequiresUser;
import com.backlogtracker.productcatalog.colorway.dto.ColorwayView;
import com.backlogtracker.productcatalog.colorway.dto.CreateColorwayRequest;
import com.backlogtracker.productcatalog.colorway.service.ColorwayService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/productcatalog/groups/{groupId}/colorways")
@RequiresUser
@RequiredArgsConstructor
public class ColorwayController {

    private final ColorwayService colorwayService;

    @GetMapping
    public List<ColorwayView> list(@PathVariable String groupId, @AuthenticationPrincipal AuthUser actor) {
        return colorwayService.list(groupId, actor.id());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ColorwayView create(@PathVariable String groupId, @RequestBody CreateColorwayRequest request,
                               @AuthenticationPrincipal AuthUser actor) {
        return colorwayService.create(groupId, actor.id(), request);
    }

    @PutMapping("/{colorwayId}")
    public ColorwayView update(@PathVariable String groupId, @PathVariable String colorwayId,
                               @RequestBody CreateColorwayRequest request, @AuthenticationPrincipal AuthUser actor) {
        return colorwayService.update(groupId, actor.id(), colorwayId, request);
    }

    @PostMapping("/{colorwayId}/promote")
    public ColorwayView promote(@PathVariable String groupId, @PathVariable String colorwayId,
                                @AuthenticationPrincipal AuthUser actor) {
        return colorwayService.promote(groupId, actor.id(), colorwayId);
    }

    @DeleteMapping("/{colorwayId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String groupId, @PathVariable String colorwayId,
                       @AuthenticationPrincipal AuthUser actor) {
        colorwayService.delete(groupId, actor.id(), colorwayId);
    }
}
