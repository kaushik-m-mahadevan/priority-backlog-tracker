package com.backlogtracker.backlogtracker.archive.web;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.backlogtracker.backlogtracker.archive.dto.ArchiveRequestView;
import com.backlogtracker.backlogtracker.archive.dto.RaiseArchiveRequest;
import com.backlogtracker.backlogtracker.archive.service.ArchiveRequestService;
import com.backlogtracker.commons.security.AuthUser;
import com.backlogtracker.commons.security.RequiresUser;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiresUser
@RequiredArgsConstructor
public class ArchiveRequestController {

    private final ArchiveRequestService service;

    /** Raise a request to archive an active item — every member must then approve it. */
    @PostMapping("/api/items/{itemId}/archive-requests")
    @ResponseStatus(HttpStatus.CREATED)
    public ArchiveRequestView raise(@PathVariable String itemId,
                                    @Valid @RequestBody(required = false) RaiseArchiveRequest body,
                                    @AuthenticationPrincipal AuthUser actor) {
        String note = body == null ? null : body.note();
        return ArchiveRequestView.of(service.create(itemId, actor, note));
    }

    /** Pending archive requests in a group (drives the row markers + the bell). */
    @GetMapping("/api/archive-requests")
    public List<ArchiveRequestView> pending(@RequestParam String groupId,
                                            @AuthenticationPrincipal AuthUser actor) {
        return service.pendingForGroup(groupId, actor).stream()
                .map(ArchiveRequestView::of).toList();
    }

    @PostMapping("/api/archive-requests/{id}/approve")
    public ArchiveRequestView approve(@PathVariable String id,
                                      @AuthenticationPrincipal AuthUser actor) {
        return ArchiveRequestView.of(service.approve(id, actor));
    }

    @PostMapping("/api/archive-requests/{id}/reject")
    public ArchiveRequestView reject(@PathVariable String id,
                                     @AuthenticationPrincipal AuthUser actor) {
        return ArchiveRequestView.of(service.reject(id, actor));
    }
}
