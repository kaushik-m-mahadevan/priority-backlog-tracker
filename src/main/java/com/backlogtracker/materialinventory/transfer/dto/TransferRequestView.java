package com.backlogtracker.materialinventory.transfer.dto;

import java.time.Instant;
import java.util.List;

import com.backlogtracker.materialinventory.transfer.domain.LineStatus;
import com.backlogtracker.materialinventory.transfer.domain.TransferRequest;

public record TransferRequestView(String id, String requesterId, String targetUserId,
                                  List<LineView> lines, Instant createdAt) {

    public record ShipmentView(String shipmentId, double quantity, String notes, Instant sentAt, Instant receivedAt) {
        static ShipmentView of(TransferRequest.Shipment s) {
            return new ShipmentView(s.getShipmentId(), s.getQuantity(), s.getNotes(), s.getSentAt(), s.getReceivedAt());
        }
    }

    /** {@code sentQuantity}/{@code receivedQuantity} are computed sums over this line's own
     *  shipments — the frontend shouldn't have to re-derive them from the raw list. */
    public record LineView(String lineId, String yarnTypeId, double requestedQuantity, LineStatus status,
                           double sentQuantity, double receivedQuantity, List<ShipmentView> shipments) {
        static LineView of(TransferRequest.TransferLine l) {
            double sent = l.getShipments().stream().mapToDouble(TransferRequest.Shipment::getQuantity).sum();
            double received = l.getShipments().stream()
                    .filter(s -> s.getReceivedAt() != null)
                    .mapToDouble(TransferRequest.Shipment::getQuantity)
                    .sum();
            return new LineView(l.getLineId(), l.getYarnTypeId(), l.getRequestedQuantity(), l.getStatus(),
                    sent, received, l.getShipments().stream().map(ShipmentView::of).toList());
        }
    }

    public static TransferRequestView of(TransferRequest r) {
        return new TransferRequestView(r.getId(), r.getRequesterId(), r.getTargetUserId(),
                r.getLines().stream().map(LineView::of).toList(), r.getCreatedAt());
    }
}
