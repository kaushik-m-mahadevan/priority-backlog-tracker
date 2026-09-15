package com.backlogtracker.materialinventory.inventory.web;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.backlogtracker.commons.security.AuthUser;
import com.backlogtracker.commons.security.RequiresUser;
import com.backlogtracker.materialinventory.inventory.dto.InventoryEntryView;
import com.backlogtracker.materialinventory.inventory.dto.SetInventoryQuantityRequest;
import com.backlogtracker.materialinventory.inventory.service.InventoryService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/materialinventory/groups/{groupId}/inventory")
@RequiresUser
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;

    /** Business-wide — every member's inventory, not just the caller's (design decision:
     *  full transparency). */
    @GetMapping
    public List<InventoryEntryView> listAll(@PathVariable String groupId, @AuthenticationPrincipal AuthUser actor) {
        return inventoryService.listAll(groupId, actor.id());
    }

    @GetMapping("/mine")
    public List<InventoryEntryView> mine(@PathVariable String groupId, @AuthenticationPrincipal AuthUser actor) {
        return inventoryService.mine(groupId, actor.id());
    }

    /** Only the caller may set their own quantity — there's no path to set anyone else's. */
    @PutMapping("/mine/{yarnTypeId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void setMyQuantity(@PathVariable String groupId, @PathVariable String yarnTypeId,
                              @RequestBody SetInventoryQuantityRequest request, @AuthenticationPrincipal AuthUser actor) {
        inventoryService.setMyQuantity(groupId, actor.id(), yarnTypeId, request.quantity());
    }
}
