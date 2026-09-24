package com.backlogtracker.materialinventory.assignment.web;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.backlogtracker.commons.security.AuthUser;
import com.backlogtracker.commons.security.RequiresUser;
import com.backlogtracker.materialinventory.assignment.dto.CreateMaterialAssignmentRequest;
import com.backlogtracker.materialinventory.assignment.dto.MaterialAssignmentView;
import com.backlogtracker.materialinventory.assignment.service.MaterialAssignmentService;

import lombok.RequiredArgsConstructor;

/** mb-21: propose/accept yarn hand-off between two named members. */
@RestController
@RequestMapping("/api/materialinventory/groups/{groupId}/assignments")
@RequiresUser
@RequiredArgsConstructor
public class MaterialAssignmentController {

    private final MaterialAssignmentService assignmentService;

    @GetMapping
    public List<MaterialAssignmentView> list(@PathVariable String groupId, @AuthenticationPrincipal AuthUser actor) {
        return assignmentService.list(groupId, actor.id());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MaterialAssignmentView propose(@PathVariable String groupId, @RequestBody CreateMaterialAssignmentRequest request,
                                          @AuthenticationPrincipal AuthUser actor) {
        return assignmentService.propose(groupId, actor.id(), request);
    }

    @PostMapping("/{assignmentId}/accept")
    public MaterialAssignmentView accept(@PathVariable String groupId, @PathVariable String assignmentId,
                                         @AuthenticationPrincipal AuthUser actor) {
        return assignmentService.accept(groupId, actor.id(), assignmentId);
    }

    @PostMapping("/{assignmentId}/reject")
    public MaterialAssignmentView reject(@PathVariable String groupId, @PathVariable String assignmentId,
                                         @AuthenticationPrincipal AuthUser actor) {
        return assignmentService.reject(groupId, actor.id(), assignmentId);
    }

    @PostMapping("/{assignmentId}/cancel")
    public MaterialAssignmentView cancel(@PathVariable String groupId, @PathVariable String assignmentId,
                                         @AuthenticationPrincipal AuthUser actor) {
        return assignmentService.cancel(groupId, actor.id(), assignmentId);
    }
}
