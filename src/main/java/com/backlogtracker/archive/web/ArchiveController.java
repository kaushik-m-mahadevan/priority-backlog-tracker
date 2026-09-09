package com.backlogtracker.archive.web;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

import com.backlogtracker.archive.domain.ArchivedItem;
import com.backlogtracker.archive.domain.TerminalStatus;
import com.backlogtracker.archive.dto.ArchivedItemView;
import com.backlogtracker.archive.dto.CompleteItemRequest;
import com.backlogtracker.archive.repository.ArchivedItemRepository;
import com.backlogtracker.archive.service.ArchiveService;
import com.backlogtracker.common.web.PageResponse;
import com.backlogtracker.security.AuthUser;
import com.backlogtracker.security.RequiresUser;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class ArchiveController {

    private final ArchiveService archiveService;
    private final ArchivedItemRepository archivedItems;

    /** Completes an item — physically moves it to archivedItems (design §24). */
    @PostMapping("/api/items/{id}/complete")
    @RequiresUser
    public ArchivedItemView complete(@PathVariable String id,
                                     @Valid @RequestBody CompleteItemRequest request,
                                     @AuthenticationPrincipal AuthUser actor) {
        TerminalStatus terminal;
        try {
            terminal = TerminalStatus.valueOf(request.terminalStatus().trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "terminalStatus must be RESOLVED, REJECTED or ARCHIVED (got '"
                            + request.terminalStatus() + "')");
        }
        return ArchivedItemView.of(archiveService.complete(id, terminal, actor));
    }

    /** The "Completed Items" view (design §24), newest first, paginated. */
    @GetMapping("/api/archived")
    public PageResponse<ArchivedItemView> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        int p = Math.max(0, page);
        int s = Math.min(Math.max(1, size), 200);
        Page<ArchivedItem> found = archivedItems.findAllByOrderByMovedAtDesc(PageRequest.of(p, s));
        return PageResponse.of(
                found.getContent().stream().map(ArchivedItemView::of).toList(),
                p, s, found.getTotalElements());
    }
}
