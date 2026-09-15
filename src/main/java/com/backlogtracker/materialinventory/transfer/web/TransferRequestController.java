package com.backlogtracker.materialinventory.transfer.web;

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
import com.backlogtracker.materialinventory.transfer.dto.CreateTransferRequestRequest;
import com.backlogtracker.materialinventory.transfer.dto.FulfillTransferRequest;
import com.backlogtracker.materialinventory.transfer.dto.TransferRequestView;
import com.backlogtracker.materialinventory.transfer.service.TransferRequestService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/materialinventory/groups/{groupId}/transfers")
@RequiresUser
@RequiredArgsConstructor
public class TransferRequestController {

    private final TransferRequestService transferRequestService;

    @GetMapping
    public List<TransferRequestView> list(@PathVariable String groupId, @AuthenticationPrincipal AuthUser actor) {
        return transferRequestService.list(groupId, actor.id());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TransferRequestView create(@PathVariable String groupId, @RequestBody CreateTransferRequestRequest request,
                                      @AuthenticationPrincipal AuthUser actor) {
        return transferRequestService.create(groupId, actor.id(), request);
    }

    @PostMapping("/{requestId}/fulfill")
    public TransferRequestView fulfill(@PathVariable String groupId, @PathVariable String requestId,
                                       @RequestBody FulfillTransferRequest request, @AuthenticationPrincipal AuthUser actor) {
        return transferRequestService.fulfill(groupId, actor.id(), requestId, request.quantity());
    }

    @PostMapping("/{requestId}/complete")
    public TransferRequestView complete(@PathVariable String groupId, @PathVariable String requestId,
                                        @AuthenticationPrincipal AuthUser actor) {
        return transferRequestService.markComplete(groupId, actor.id(), requestId);
    }

    @PostMapping("/{requestId}/cancel")
    public TransferRequestView cancel(@PathVariable String groupId, @PathVariable String requestId,
                                      @AuthenticationPrincipal AuthUser actor) {
        return transferRequestService.cancel(groupId, actor.id(), requestId);
    }
}
