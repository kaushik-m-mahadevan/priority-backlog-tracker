package com.backlogtracker.materialinventory.yarn.web;

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
import com.backlogtracker.materialinventory.yarn.dto.CreateYarnTypeRequest;
import com.backlogtracker.materialinventory.yarn.dto.YarnTypeView;
import com.backlogtracker.materialinventory.yarn.service.YarnTypeService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/materialinventory/groups/{groupId}/yarn-types")
@RequiresUser
@RequiredArgsConstructor
public class YarnTypeController {

    private final YarnTypeService yarnTypeService;

    @GetMapping
    public List<YarnTypeView> list(@PathVariable String groupId, @AuthenticationPrincipal AuthUser actor) {
        return yarnTypeService.list(groupId, actor.id());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public YarnTypeView create(@PathVariable String groupId, @RequestBody CreateYarnTypeRequest request,
                               @AuthenticationPrincipal AuthUser actor) {
        return yarnTypeService.create(groupId, actor.id(), request);
    }

    @PutMapping("/{yarnTypeId}")
    public YarnTypeView update(@PathVariable String groupId, @PathVariable String yarnTypeId,
                               @RequestBody CreateYarnTypeRequest request, @AuthenticationPrincipal AuthUser actor) {
        return yarnTypeService.update(groupId, actor.id(), yarnTypeId, request);
    }

    @DeleteMapping("/{yarnTypeId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String groupId, @PathVariable String yarnTypeId,
                       @AuthenticationPrincipal AuthUser actor) {
        yarnTypeService.delete(groupId, actor.id(), yarnTypeId);
    }
}
