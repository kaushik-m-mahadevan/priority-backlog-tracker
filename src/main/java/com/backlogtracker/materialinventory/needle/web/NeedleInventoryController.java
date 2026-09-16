package com.backlogtracker.materialinventory.needle.web;

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
import com.backlogtracker.materialinventory.needle.dto.NeedleInventoryEntryView;
import com.backlogtracker.materialinventory.needle.dto.SetNeedleQuantityRequest;
import com.backlogtracker.materialinventory.needle.service.NeedleInventoryService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/materialinventory/groups/{groupId}/needle-inventory")
@RequiresUser
@RequiredArgsConstructor
public class NeedleInventoryController {

    private final NeedleInventoryService needleInventoryService;

    @GetMapping
    public List<NeedleInventoryEntryView> listAll(@PathVariable String groupId, @AuthenticationPrincipal AuthUser actor) {
        return needleInventoryService.listAll(groupId, actor.id());
    }

    @GetMapping("/mine")
    public List<NeedleInventoryEntryView> mine(@PathVariable String groupId, @AuthenticationPrincipal AuthUser actor) {
        return needleInventoryService.mine(groupId, actor.id());
    }

    @PutMapping("/mine/{needleTypeId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void setMyQuantity(@PathVariable String groupId, @PathVariable String needleTypeId,
                              @RequestBody SetNeedleQuantityRequest request, @AuthenticationPrincipal AuthUser actor) {
        needleInventoryService.setMyQuantity(groupId, actor.id(), needleTypeId, request.quantity());
    }
}
