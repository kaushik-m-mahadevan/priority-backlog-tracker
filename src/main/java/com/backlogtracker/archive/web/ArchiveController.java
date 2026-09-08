package com.backlogtracker.archive.web;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

import com.backlogtracker.archive.domain.TerminalStatus;
import com.backlogtracker.archive.dto.ArchivedItemView;
import com.backlogtracker.archive.dto.CompleteItemRequest;
import com.backlogtracker.archive.service.ArchiveService;
import com.backlogtracker.security.AuthUser;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class ArchiveController {

    private final ArchiveService archiveService;

    /** Completes an item — physically moves it to archivedItems (design §24). */
    @PostMapping("/api/items/{id}/complete")
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

    /** The "Completed Items" view (design §24). */
    @GetMapping("/api/archived")
    public List<ArchivedItemView> list() {
        return archiveService.list().stream().map(ArchivedItemView::of).toList();
    }
}
