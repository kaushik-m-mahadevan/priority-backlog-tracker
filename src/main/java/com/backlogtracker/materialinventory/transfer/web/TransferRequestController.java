package com.backlogtracker.materialinventory.transfer.web;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.backlogtracker.commons.security.AuthUser;
import com.backlogtracker.commons.security.RequiresUser;
import com.backlogtracker.materialinventory.transfer.dto.CreateTransferRequestRequest;
import com.backlogtracker.materialinventory.transfer.dto.SendShipmentRequest;
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

    @PostMapping("/{requestId}/lines/{lineId}/send")
    public TransferRequestView send(@PathVariable String groupId, @PathVariable String requestId, @PathVariable String lineId,
                                    @RequestBody SendShipmentRequest request, @AuthenticationPrincipal AuthUser actor) {
        return transferRequestService.send(groupId, actor.id(), requestId, lineId, request);
    }

    @PostMapping("/{requestId}/lines/{lineId}/shipments/{shipmentId}/receive")
    public TransferRequestView confirmReceived(@PathVariable String groupId, @PathVariable String requestId,
                                               @PathVariable String lineId, @PathVariable String shipmentId,
                                               @AuthenticationPrincipal AuthUser actor) {
        return transferRequestService.confirmReceived(groupId, actor.id(), requestId, lineId, shipmentId);
    }

    @PostMapping("/{requestId}/lines/{lineId}/close")
    public TransferRequestView closeLine(@PathVariable String groupId, @PathVariable String requestId, @PathVariable String lineId,
                                         @AuthenticationPrincipal AuthUser actor) {
        return transferRequestService.closeLine(groupId, actor.id(), requestId, lineId);
    }

    @PostMapping("/{requestId}/cancel")
    public TransferRequestView cancel(@PathVariable String groupId, @PathVariable String requestId,
                                      @AuthenticationPrincipal AuthUser actor) {
        return transferRequestService.cancel(groupId, actor.id(), requestId);
    }
}
