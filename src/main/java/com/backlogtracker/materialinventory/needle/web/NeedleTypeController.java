package com.backlogtracker.materialinventory.needle.web;

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
import com.backlogtracker.materialinventory.needle.dto.CreateNeedleTypeRequest;
import com.backlogtracker.materialinventory.needle.dto.NeedleTypeView;
import com.backlogtracker.materialinventory.needle.service.NeedleTypeService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/materialinventory/groups/{groupId}/needle-types")
@RequiresUser
@RequiredArgsConstructor
public class NeedleTypeController {

    private final NeedleTypeService needleTypeService;

    @GetMapping
    public List<NeedleTypeView> list(@PathVariable String groupId, @AuthenticationPrincipal AuthUser actor) {
        return needleTypeService.list(groupId, actor.id());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public NeedleTypeView create(@PathVariable String groupId, @RequestBody CreateNeedleTypeRequest request,
                                 @AuthenticationPrincipal AuthUser actor) {
        return needleTypeService.create(groupId, actor.id(), request);
    }

    @PutMapping("/{needleTypeId}")
    public NeedleTypeView update(@PathVariable String groupId, @PathVariable String needleTypeId,
                                 @RequestBody CreateNeedleTypeRequest request, @AuthenticationPrincipal AuthUser actor) {
        return needleTypeService.update(groupId, actor.id(), needleTypeId, request);
    }

    @DeleteMapping("/{needleTypeId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String groupId, @PathVariable String needleTypeId,
                       @AuthenticationPrincipal AuthUser actor) {
        needleTypeService.delete(groupId, actor.id(), needleTypeId);
    }
}
