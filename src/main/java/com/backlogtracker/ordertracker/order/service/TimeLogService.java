package com.backlogtracker.ordertracker.order.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.backlogtracker.ordertracker.order.domain.Order;
import com.backlogtracker.ordertracker.order.domain.OrderType;
import com.backlogtracker.ordertracker.order.dto.AddTimeLogEntryRequest;

/**
 * Where a logged-time entry belongs on an order (bdup-7), extracted from
 * {@code OrderService} as a self-contained collaborator: RESEARCH is always whole-order;
 * CRAFTING/ASSEMBLY go on the order itself for an INDIVIDUAL order, or on the named
 * variant for a BULK order (design decision, see {@link Order#timeLogEntries}). Operates
 * on an already-loaded, already-membership-checked {@link Order} — {@code OrderService}
 * itself still owns loading the order, persisting it, and building the response view.
 */
@Service
class TimeLogService {

    private static final double QUARTER_STEP_EPSILON = 1e-9;

    Order.TimeLogEntry addEntry(Order order, AddTimeLogEntryRequest request, String loggedByCreatorId, Instant now) {
        double hours = requireQuarterStepHours(request.hours());
        Order.TimeLogEntry entry = Order.TimeLogEntry.builder()
                .entryId(UUID.randomUUID().toString())
                .stage(request.stage())
                .hours(hours)
                .date(request.date() == null ? now : request.date())
                .loggedByCreatorId(loggedByCreatorId)
                .note(request.note() == null || request.note().isBlank() ? null : request.note().trim())
                .build();

        if (request.stage() == Order.TimeStage.RESEARCH) {
            if (request.variantId() != null || request.componentId() != null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Research time is whole-order — variantId/componentId must not be set");
            }
            order.getTimeLogEntries().add(entry);
        } else if (order.getOrderType() == OrderType.INDIVIDUAL) {
            if (request.variantId() != null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This order has no variants");
            }
            if (request.componentId() != null) {
                if (request.stage() != Order.TimeStage.CRAFTING) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "componentId only applies to crafting time");
                }
                requireComponent(order.getComponents(), request.componentId()).getTimeLogEntries().add(entry);
            } else {
                order.getTimeLogEntries().add(entry);
            }
        } else {
            requireBulk(order);
            if (request.variantId() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "variantId is required for crafting/assembly time on a bulk order");
            }
            Order.Variant variant = order.getBulkDetails().getVariants().stream()
                    .filter(v -> v.getVariantId().equals(request.variantId())).findFirst()
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Variant not found"));
            if (request.componentId() != null) {
                if (request.stage() != Order.TimeStage.CRAFTING) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "componentId only applies to crafting time");
                }
                requireComponent(variant.getComponents(), request.componentId()).getTimeLogEntries().add(entry);
            } else {
                variant.getTimeLogEntries().add(entry);
            }
        }
        return entry;
    }

    boolean removeEntry(Order order, String entryId) {
        boolean removed = order.getTimeLogEntries().removeIf(e -> e.getEntryId().equals(entryId));
        if (!removed) {
            for (Order.Component c : order.getComponents()) {
                if (c.getTimeLogEntries().removeIf(e -> e.getEntryId().equals(entryId))) {
                    removed = true;
                    break;
                }
            }
        }
        if (!removed && order.getBulkDetails() != null) {
            outer:
            for (Order.Variant v : order.getBulkDetails().getVariants()) {
                if (v.getTimeLogEntries().removeIf(e -> e.getEntryId().equals(entryId))) {
                    removed = true;
                    break;
                }
                for (Order.Component c : v.getComponents()) {
                    if (c.getTimeLogEntries().removeIf(e -> e.getEntryId().equals(entryId))) {
                        removed = true;
                        break outer;
                    }
                }
            }
        }
        return removed;
    }

    private static Order.Component requireComponent(List<Order.Component> components, String componentId) {
        return components.stream().filter(c -> c.getComponentId().equals(componentId)).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Component not found"));
    }

    private static void requireBulk(Order order) {
        if (order.getOrderType() != OrderType.BULK) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This endpoint is for bulk orders only");
        }
    }

    private static double requireQuarterStepHours(double hours) {
        if (hours <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "hours must be greater than zero");
        }
        double quarters = hours * 4;
        if (Math.abs(quarters - Math.round(quarters)) > QUARTER_STEP_EPSILON) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "hours must be in quarter-hour steps (e.g. 0.25, 1.5)");
        }
        return Math.round(quarters) / 4.0;
    }
}
