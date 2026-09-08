package com.backlogtracker.item.web;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.backlogtracker.item.domain.ItemStatus;
import com.backlogtracker.item.dto.CreateItemRequest;
import com.backlogtracker.item.dto.ItemView;
import com.backlogtracker.item.dto.StatusChangeRequest;
import com.backlogtracker.item.dto.UpdateItemRequest;
import com.backlogtracker.item.service.ItemService;
import com.backlogtracker.security.AuthUser;
import com.backlogtracker.security.RequiresContributor;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/items")
@RequiredArgsConstructor
public class ItemController {

    private final ItemService itemService;

    /** Live shared items (Backlog + In Progress). */
    @GetMapping
    public List<ItemView> list() {
        return itemService.listShared().stream().map(ItemView::of).toList();
    }

    @GetMapping("/{id}")
    public ItemView get(@PathVariable String id) {
        return ItemView.of(itemService.get(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequiresContributor
    public ItemView create(@Valid @RequestBody CreateItemRequest request,
                           @AuthenticationPrincipal AuthUser actor) {
        return ItemView.of(itemService.create(request, actor));
    }

    @PutMapping("/{id}")
    @RequiresContributor
    public ItemView update(@PathVariable String id,
                           @Valid @RequestBody UpdateItemRequest request,
                           @AuthenticationPrincipal AuthUser actor) {
        return ItemView.of(itemService.update(id, request, actor));
    }

    @PatchMapping("/{id}/status")
    @RequiresContributor
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
