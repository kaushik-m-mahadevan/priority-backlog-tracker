package com.backlogtracker.backlogtracker.item.web;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
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

import com.backlogtracker.commons.web.PageResponse;
import com.backlogtracker.commons.group.service.GroupService;
import com.backlogtracker.backlogtracker.item.domain.Item;
import com.backlogtracker.backlogtracker.item.domain.ItemStatus;
import com.backlogtracker.backlogtracker.item.dto.CreateItemRequest;
import com.backlogtracker.backlogtracker.item.dto.ItemView;
import com.backlogtracker.backlogtracker.item.dto.StatusChangeRequest;
import com.backlogtracker.backlogtracker.item.dto.UpdateItemRequest;
import com.backlogtracker.backlogtracker.item.service.ItemService;
import com.backlogtracker.backlogtracker.search.ItemQueryService;
import com.backlogtracker.commons.security.AuthUser;
import com.backlogtracker.commons.security.RequiresUser;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/items")
@RequiredArgsConstructor
public class ItemController {

    private final ItemService itemService;
    private final ItemQueryService itemQueryService;
    private final GroupService groupService;

    /**
     * One group's live items (Backlog + In Progress), searched / filtered / paginated.
     * {@code groupId} is required; {@code q} / {@code owner} / {@code category} /
     * {@code priority} / {@code status} / {@code page} / {@code size} are optional.
     */
    @GetMapping
    public PageResponse<ItemView> list(
            @RequestParam String groupId,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String owner,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String priority,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @AuthenticationPrincipal AuthUser actor) {
        groupService.requireMember(groupId, actor.id());
        ItemStatus st;
        try {
            st = status == null || status.isBlank() ? null : ItemStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("status must be BACKLOG or IN_PROGRESS (got '" + status + "')");
        }
        var res = itemQueryService.search(groupId, q, owner, category, priority, st, page, size);
        return PageResponse.of(res.content().stream().map(ItemView::of).toList(),
                res.page(), res.size(), res.total());
    }

    @GetMapping("/{id}")
    public ItemView get(@PathVariable String id, @AuthenticationPrincipal AuthUser actor) {
        return ItemView.of(itemService.get(id, actor));
    }

    /** Used by Order Tracker's "add to group" flow to check whether an order is already
     *  linked to an item in the chosen group, before offering to create one. Called
     *  directly from the browser — this applet never calls into ordertracker's backend. */
    @GetMapping("/by-linked-order/{linkedOrderId}")
    public ItemView byLinkedOrder(@PathVariable String linkedOrderId, @RequestParam String groupId,
                                  @AuthenticationPrincipal AuthUser actor) {
        Item item = itemService.findByLinkedOrder(groupId, linkedOrderId, actor);
        return item == null ? null : ItemView.of(item);
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

    /** Pin an item — it floats to the top of the Pecking Order for everyone. */
    @PostMapping("/{id}/pin")
    @RequiresUser
    public ItemView pin(@PathVariable String id, @AuthenticationPrincipal AuthUser actor) {
        return ItemView.of(itemService.setPinned(id, actor, true));
    }

    /** Remove an item's pin. */
    @DeleteMapping("/{id}/pin")
    @RequiresUser
    public ItemView unpin(@PathVariable String id, @AuthenticationPrincipal AuthUser actor) {
        return ItemView.of(itemService.setPinned(id, actor, false));
    }
}
