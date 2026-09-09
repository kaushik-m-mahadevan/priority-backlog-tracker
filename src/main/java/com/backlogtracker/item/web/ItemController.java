package com.backlogtracker.item.web;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.backlogtracker.common.web.PageResponse;
import com.backlogtracker.item.domain.ItemScope;
import com.backlogtracker.item.domain.ItemStatus;
import com.backlogtracker.item.dto.CreateItemRequest;
import com.backlogtracker.item.dto.ItemView;
import com.backlogtracker.item.dto.StatusChangeRequest;
import com.backlogtracker.item.dto.UpdateItemRequest;
import com.backlogtracker.item.service.ItemService;
import com.backlogtracker.search.ItemQueryService;
import com.backlogtracker.security.AuthUser;
import com.backlogtracker.security.RequiresUser;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/items")
@RequiredArgsConstructor
public class ItemController {

    private final ItemService itemService;
    private final ItemQueryService itemQueryService;

    /**
     * Live items (Backlog + In Progress), searched / filtered / paginated (design §21).
     * All params optional: {@code q} (title contains), {@code owner}, {@code category},
     * {@code priority}, {@code status}, {@code page} (0-based), {@code size}.
     */
    @GetMapping
    public PageResponse<ItemView> list(
            @RequestParam(defaultValue = "shared") String scope,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String owner,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String priority,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @AuthenticationPrincipal AuthUser actor) {
        if (!"shared".equalsIgnoreCase(scope)) {
            throw new IllegalArgumentException("Only scope=shared is supported yet");
        }
        ItemStatus st = status == null || status.isBlank()
                ? null
                : ItemStatus.valueOf(status.trim().toUpperCase());
        var res = itemQueryService.search(ItemScope.SHARED, actor.id(), q, owner, category,
                priority, st, page, size);
        return PageResponse.of(res.content().stream().map(ItemView::of).toList(),
                res.page(), res.size(), res.total());
    }

    @GetMapping("/{id}")
    public ItemView get(@PathVariable String id) {
        return ItemView.of(itemService.get(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequiresUser
    public ItemView create(@Valid @RequestBody CreateItemRequest request,
                           @AuthenticationPrincipal AuthUser actor) {
        return ItemView.of(itemService.create(request, actor));
    }

    @PutMapping("/{id}")
    @RequiresUser
    public ItemView update(@PathVariable String id,
                           @Valid @RequestBody UpdateItemRequest request,
                           @AuthenticationPrincipal AuthUser actor) {
        return ItemView.of(itemService.update(id, request, actor));
    }

    @PatchMapping("/{id}/status")
    @RequiresUser
    public ItemView changeStatus(@PathVariable String id,
                                 @Valid @RequestBody StatusChangeRequest request,
                                 @AuthenticationPrincipal AuthUser actor) {
        ItemStatus status;
        try {
            status = ItemStatus.valueOf(request.status().trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "status must be BACKLOG or IN_PROGRESS (got '" + request.status() + "')");
        }
        return ItemView.of(itemService.changeStatus(id, status, actor));
    }
}
