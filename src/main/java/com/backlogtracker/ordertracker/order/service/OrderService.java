package com.backlogtracker.ordertracker.order.service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.backlogtracker.commons.crypto.EncryptedString;
import com.backlogtracker.commons.group.service.GroupService;
import com.backlogtracker.commons.pattern.domain.Pattern;
import com.backlogtracker.ordertracker.customer.service.CustomerService;
import com.backlogtracker.ordertracker.master.domain.BusinessConfig;
import com.backlogtracker.ordertracker.master.domain.Creator;
import com.backlogtracker.ordertracker.master.domain.PresetOption;
import com.backlogtracker.ordertracker.master.service.BusinessConfigService;
import com.backlogtracker.ordertracker.master.service.CreatorService;
import com.backlogtracker.ordertracker.master.service.MasterDataService;
import com.backlogtracker.ordertracker.order.domain.Order;
import com.backlogtracker.ordertracker.order.domain.Order.LineItem;
import com.backlogtracker.ordertracker.order.domain.Order.MandatoryItem;
import com.backlogtracker.ordertracker.order.domain.Order.SplitLine;
import com.backlogtracker.ordertracker.order.domain.Order.StageAssignment;
import com.backlogtracker.ordertracker.order.domain.Order.Variant;
import com.backlogtracker.ordertracker.order.domain.OrderChangeLog;
import com.backlogtracker.ordertracker.order.domain.OrderStatus;
import com.backlogtracker.ordertracker.order.domain.OrderType;
import com.backlogtracker.ordertracker.order.dto.AddPaymentRequest;
import com.backlogtracker.ordertracker.order.dto.AddTimeLogEntryRequest;
import com.backlogtracker.ordertracker.order.dto.CreateOrderRequest;
import com.backlogtracker.ordertracker.order.dto.MarkShipmentStopRequest;
import com.backlogtracker.ordertracker.order.dto.OrderView;
import com.backlogtracker.ordertracker.order.dto.ShipmentPlanRequest;
import com.backlogtracker.ordertracker.order.dto.UpdateBulkDetailsRequest;
import com.backlogtracker.ordertracker.order.dto.UpdateBulkSplitProgressRequest;
import com.backlogtracker.ordertracker.order.dto.UpdateBulkStageProgressRequest;
import com.backlogtracker.ordertracker.order.dto.UpdateOrderRequest;
import com.backlogtracker.ordertracker.order.dto.UpdateOrderStatusRequest;
import com.backlogtracker.ordertracker.order.dto.UpdateStageAssignmentRequest;
import com.backlogtracker.ordertracker.order.validation.OrderBusinessRules;
import com.backlogtracker.ordertracker.order.repository.OrderChangeLogRepository;
import com.backlogtracker.ordertracker.order.repository.OrderRepository;

import lombok.RequiredArgsConstructor;

/**
 * Order CRUD and lifecycle for both individual and bulk orders, sharing one collection and
 * one base schema (spec §5, design principle #4).
 */
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository repository;
    private final OrderChangeLogRepository changeLogRepository;
    private final GroupService groupService;
    private final CustomerService customerService;
    private final CreatorService creatorService;
    private final BusinessConfigService businessConfigService;
    private final MasterDataService masterDataService;
    private final OrderNumberService orderNumberService;
    private final OrderCalculator calculator;
    private final OrderBusinessRules rules;
    private final Clock clock;

    public List<OrderView> all(String groupId, String userId) {
        groupService.requireMember(groupId, userId);
        BusinessConfig cfg = businessConfigService.get(groupId, userId);
        return repository.findByGroupId(groupId).stream().map(o -> view(o, userId, cfg)).toList();
    }

    /** Orders where the caller's own Creator profile has assigned work — individual orders
     *  via {@code stageAssignments}, bulk orders via each variant's {@code splitAllocation}.
     *  {@code completionFilter}: "pending" (default, incomplete-for-me only), "done"
     *  (complete-for-me only), or "all". Date range is optional, on {@code orderReceivedDate}. */
    public List<OrderView> myWork(String groupId, String userId, String completionFilter, Instant from, Instant to) {
        groupService.requireMember(groupId, userId);
        Creator me = creatorService.myProfile(groupId, userId);
        if (me == null) {
            return List.of();
        }
        BusinessConfig cfg = businessConfigService.get(groupId, userId);
        String filter = completionFilter == null ? "pending" : completionFilter;
        return repository.findByGroupId(groupId).stream()
                .filter(o -> isMyWork(o, me.getId()))
                .filter(o -> from == null || (o.getOrderReceivedDate() != null && !o.getOrderReceivedDate().isBefore(from)))
                .filter(o -> to == null || (o.getOrderReceivedDate() != null && !o.getOrderReceivedDate().isAfter(to)))
                .filter(o -> switch (filter) {
                    case "done" -> isCompleteForMe(o, me.getId(), cfg);
                    case "all" -> true;
                    default -> !isCompleteForMe(o, me.getId(), cfg);
                })
                .map(o -> view(o, userId, cfg))
                .toList();
    }

    private boolean isMyWork(Order order, String creatorId) {
        if (order.getOrderType() == OrderType.INDIVIDUAL) {
            return order.getStageAssignments().stream()
                    .anyMatch(sa -> creatorId.equals(sa.getAssignedCreatorId()));
        }
        if (order.getBulkDetails() == null) {
            return false;
        }
        return order.getBulkDetails().getVariants().stream()
                .flatMap(v -> v.getSplitAllocation().stream())
                .anyMatch(s -> creatorId.equals(s.getCreatorId()));
    }

    private boolean isCompleteForMe(Order order, String creatorId, BusinessConfig cfg) {
        if (order.getOrderType() == OrderType.INDIVIDUAL) {
            return order.getStageAssignments().stream()
                    .filter(sa -> creatorId.equals(sa.getAssignedCreatorId()))
                    .allMatch(sa -> sa.getUnitsCompleted() >= sa.getTotalUnits());
        }
        if (order.getBulkDetails() == null) {
            return true;
        }
        for (Variant v : order.getBulkDetails().getVariants()) {
            for (SplitLine s : v.getSplitAllocation()) {
                if (!creatorId.equals(s.getCreatorId())) {
                    continue;
                }
                for (BusinessConfig.WorkStageType stage : cfg.getWorkStages()) {
                    if (!stage.splitTracked()) {
                        continue;
                    }
                    int completed = s.getStageProgress().stream()
                            .filter(sp -> stage.stageKey().equals(sp.getStageKey()))
                            .mapToInt(Order.StageProgressEntry::getUnitsCompleted)
                            .findFirst().orElse(0);
                    if (completed < s.getQuantityAssigned()) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    public OrderView get(String groupId, String userId, String orderId) {
        groupService.requireMember(groupId, userId);
        return view(requireById(groupId, orderId), userId);
    }

    public List<OrderChangeLog> getChangeLog(String groupId, String userId, String orderId) {
        groupService.requireMember(groupId, userId);
        requireById(groupId, orderId);
        return changeLogRepository.findByOrderId(orderId).map(List::of).orElse(List.of());
    }

    // ---- Create ----

    public OrderView create(String groupId, String userId, CreateOrderRequest request) {
        groupService.requireMember(groupId, userId);
        customerService.requireExists(groupId, request.customerId());
        Creator createdBy = creatorService.requireById(groupId, request.createdByCreatorId());
        BusinessConfig cfg = businessConfigService.get(groupId, userId);
        OrderType orderType;
        try {
            orderType = OrderType.valueOf(request.orderType());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "orderType must be INDIVIDUAL or BULK (got '" + request.orderType() + "')");
        }

        Instant now = clock.instant();
        Instant orderReceivedDate = request.orderReceivedDate() == null ? now : request.orderReceivedDate();
        String orderNumber = orderNumberService.next(groupId, createdBy.getLocationCode(), createdBy.getCreatorCode(),
                orderType == OrderType.INDIVIDUAL ? cfg.getIndividualOrderTypeCode() : cfg.getBulkOrderTypeCode());

        Order.OrderBuilder builder = Order.builder()
                .groupId(groupId)
                .orderNumber(orderNumber)
                .orderType(orderType)
                .customerId(request.customerId())
                .createdByCreatorId(request.createdByCreatorId())
                .status(OrderStatus.INQUIRY)
                .itemName(request.itemName())
                .orderReceivedDate(orderReceivedDate)
                .quotedDeliveryDate(request.quotedDeliveryDate())
                .pattern(toPattern(request.pattern()))
                .researchItems(toResearchItems(request.researchItems()))
                .researchTimeHours(request.researchTimeHours())
                .assemblyPackagingInstructions(request.assemblyPackagingInstructions())
                .notes(request.notes())
                .payments(new ArrayList<>())
                .paymentStatus(calculator.derivePaymentStatus(List.of(), 0))
                .createdAt(now)
                .updatedAt(now);

        if (orderType == OrderType.INDIVIDUAL) {
            List<MandatoryItem> mandatoryItems = toMandatoryItems(request.mandatoryItems());
            List<LineItem> addOns = toLineItems(request.addOns());
            List<Order.Component> components = toComponents(groupId, userId, request.components(), List.of());
            Order.Packaging packaging = buildPackaging(groupId, userId, request.packagingPresetId(), request.itemizedPackaging());
            builder.mandatoryItems(mandatoryItems)
                    .addOns(addOns)
                    .components(components)
                    .packaging(packaging)
                    .craftingTimeHours(request.craftingTimeHours())
                    .assemblyTimeHours(request.assemblyTimeHours())
                    .costEstimate(calculator.estimateIndividual(mandatoryItems, addOns, components, packaging,
                            request.craftingTimeHours(), request.assemblyTimeHours(), request.researchTimeHours(),
                            cfg.getOverheadPercentage(), cfg.getProfitMarginPercentage(),
                            orderReceivedDate, createdBy.getHoursAvailablePerDay()))
                    .stageAssignments(cfg.getWorkStages().stream()
                            .map(s -> Order.StageAssignment.builder().stageKey(s.stageKey())
                                    .assignedCreatorId(request.createdByCreatorId()).unitsCompleted(0).totalUnits(1)
                                    .lastUpdatedAt(now).build())
                            .collect(Collectors.toList()));
        } else {
            if (request.variants() == null || request.variants().isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A bulk order needs at least one variant");
            }
            request.variants().forEach(rules::validateVariant);
            List<Variant> variants = request.variants().stream()
                    .map(v -> buildVariant(groupId, userId, v, cfg))
                    .collect(Collectors.toList());
            Order.BulkDetails details = Order.BulkDetails.builder()
                    .variants(variants)
                    .coordinatingCreatorId(request.coordinatingCreatorId() == null
                            ? request.createdByCreatorId() : request.coordinatingCreatorId())
                    .stageAssignments(cfg.getWorkStages().stream()
                            .map(s -> StageAssignment.builder().stageKey(s.stageKey())
                                    .assignedCreatorId(request.createdByCreatorId()).unitsCompleted(0).totalUnits(1)
                                    .lastUpdatedAt(now).build())
                            .collect(Collectors.toList()))
                    .stageProgress(cfg.getWorkStages().stream().filter(s -> !s.splitTracked())
                            .map(s -> Order.StageProgress.builder().stageKey(s.stageKey()).unitsCompleted(0)
                                    .totalUnits(variants.stream().mapToInt(Variant::getQuantity).sum()).build())
                            .collect(Collectors.toList()))
                    .logisticsBufferDays(request.logisticsBufferDays())
                    .build();
            recomputeBulkTotals(details, cfg, orderReceivedDate, request.researchTimeHours());
            builder.bulkDetails(details);
        }

        return view(repository.save(builder.build()), userId);
    }

    // ---- Individual-order full edit ----

    public OrderView update(String groupId, String userId, String orderId, UpdateOrderRequest request) {
        groupService.requireMember(groupId, userId);
        Order order = requireById(groupId, orderId);
        requireIndividual(order);
        BusinessConfig cfg = businessConfigService.get(groupId, userId);
        Creator createdBy = creatorService.requireById(groupId, order.getCreatedByCreatorId());

        Instant oldDueDate = order.getCostEstimate() == null ? null : order.getCostEstimate().getComputedDueDate();

        if (request.customerId() != null && !request.customerId().isBlank()) {
            customerService.requireExists(groupId, request.customerId());
            order.setCustomerId(request.customerId());
        }
        order.setItemName(request.itemName());
        order.setOrderReceivedDate(request.orderReceivedDate());
        order.setQuotedDeliveryDate(request.quotedDeliveryDate());
        order.setPattern(toPattern(request.pattern()));
        order.setResearchItems(toResearchItems(request.researchItems()));
        order.setResearchTimeHours(request.researchTimeHours());
        order.setAssemblyPackagingInstructions(request.assemblyPackagingInstructions());
        order.setNotes(request.notes());
        List<MandatoryItem> mandatoryItems = toMandatoryItems(request.mandatoryItems());
        List<LineItem> addOns = toLineItems(request.addOns());
        List<Order.Component> components = toComponents(groupId, userId, request.components(), order.getComponents());
        Order.Packaging packaging = buildPackaging(groupId, userId, request.packagingPresetId(), request.itemizedPackaging());
        order.setMandatoryItems(mandatoryItems);
        order.setAddOns(addOns);
        order.setComponents(components);
        order.setPackaging(packaging);
        order.setCraftingTimeHours(request.craftingTimeHours());
        order.setAssemblyTimeHours(request.assemblyTimeHours());
        order.setCostEstimate(calculator.estimateIndividual(mandatoryItems, addOns, components, packaging,
                request.craftingTimeHours(), request.assemblyTimeHours(), request.researchTimeHours(),
                cfg.getOverheadPercentage(), cfg.getProfitMarginPercentage(),
                order.getOrderReceivedDate() == null ? clock.instant() : order.getOrderReceivedDate(),
                createdBy.getHoursAvailablePerDay()));
        order.setUpdatedAt(clock.instant());

        Instant newDueDate = order.getCostEstimate().getComputedDueDate();
        if (oldDueDate != null && !oldDueDate.equals(newDueDate)) {
            logDeliveryDateChange(orderId, oldDueDate, newDueDate, userId);
        }
        return view(repository.save(order), userId);
    }

    // ---- Bulk-order variant edit ----

    public OrderView updateBulkDetails(String groupId, String userId, String orderId, UpdateBulkDetailsRequest request) {
        groupService.requireMember(groupId, userId);
        Order order = requireById(groupId, orderId);
        requireBulk(order);
        BusinessConfig cfg = businessConfigService.get(groupId, userId);
        Order.BulkDetails details = order.getBulkDetails();

        if (request.customerId() != null && !request.customerId().isBlank()) {
            customerService.requireExists(groupId, request.customerId());
            order.setCustomerId(request.customerId());
        }
        order.setItemName(request.itemName());
        if (request.orderReceivedDate() != null) {
            order.setOrderReceivedDate(request.orderReceivedDate());
        }
        order.setQuotedDeliveryDate(request.quotedDeliveryDate());
        order.setPattern(toPattern(request.pattern()));
        order.setResearchItems(toResearchItems(request.researchItems()));
        order.setResearchTimeHours(request.researchTimeHours());
        order.setAssemblyPackagingInstructions(request.assemblyPackagingInstructions());
        order.setNotes(request.notes());

        request.variants().forEach(rules::validateVariant);
        List<Variant> oldVariants = details.getVariants();
        List<Variant> newVariants = request.variants().stream()
                .map(v -> buildVariant(groupId, userId, v, cfg))
                .collect(Collectors.toList());

        for (Variant nv : newVariants) {
            oldVariants.stream().filter(ov -> ov.getVariantId().equals(nv.getVariantId())).findFirst()
                    .ifPresent(ov -> {
                        if (ov.getQuantity() != nv.getQuantity()) {
                            logQuantityChange(orderId, ov.getQuantity(), nv.getQuantity(), nv.getVariantId(), userId);
                        }
                        if (!sameAllocation(ov.getSplitAllocation(), nv.getSplitAllocation())) {
                            logSplitReallocation(orderId, nv.getVariantId(), ov.getSplitAllocation(),
                                    nv.getSplitAllocation(), userId);
                        }
                        // carry forward progress already recorded against unchanged stage keys
                        nv.setSplitAllocation(mergeProgress(ov.getSplitAllocation(), nv.getSplitAllocation()));
                        // same idea one level down: a component that kept its id keeps its logged time
                        nv.getComponents().forEach(nc -> ov.getComponents().stream()
                                .filter(oc -> oc.getComponentId().equals(nc.getComponentId())).findFirst()
                                .ifPresent(oc -> nc.setTimeLogEntries(new ArrayList<>(oc.getTimeLogEntries()))));
                    });
        }

        details.setVariants(newVariants);
        details.setCoordinatingCreatorId(request.coordinatingCreatorId());
        details.setLogisticsBufferDays(request.logisticsBufferDays());
        // batch-tracked stage totals track total quantity — resize without losing progress,
        // but clamp unitsCompleted down too: shrinking the total below what was already
        // marked done would otherwise report completion over 100%.
        int totalQty = newVariants.stream().mapToInt(Variant::getQuantity).sum();
        details.getStageProgress().forEach(sp -> {
            sp.setTotalUnits(totalQty);
            sp.setUnitsCompleted(Math.min(sp.getUnitsCompleted(), totalQty));
        });

        Instant oldDueDate = details.getComputedDueDate();
        recomputeBulkTotals(details, cfg, order.getOrderReceivedDate() == null ? clock.instant() : order.getOrderReceivedDate(),
                order.getResearchTimeHours());
        order.setUpdatedAt(clock.instant());

        if (oldDueDate != null && !oldDueDate.equals(details.getComputedDueDate())) {
            logDeliveryDateChange(orderId, oldDueDate, details.getComputedDueDate(), userId);
        }
        return view(repository.save(order), userId);
    }

    // ---- Status ----

    public OrderView updateStatus(String groupId, String userId, String orderId, UpdateOrderStatusRequest request) {
        groupService.requireMember(groupId, userId);
        Order order = requireById(groupId, orderId);
        if (order.getStatus() != request.status()) {
            appendChangeLog(orderId, log -> log.getOrderStatusChangeHistory().add(OrderChangeLog.StatusChange.builder()
                    .status(request.status()).changedByCreatorId(userId).changeTimestamp(clock.instant()).build()));
        }
        order.setStatus(request.status());
        if (request.status() == OrderStatus.DELIVERED && order.getActualDeliveryDate() == null) {
            order.setActualDeliveryDate(clock.instant());
        }
        order.setUpdatedAt(clock.instant());
        return view(repository.save(order), userId);
    }

    // ---- Individual stage assignment ----

    public OrderView updateStageAssignment(String groupId, String userId, String orderId, String stageKey,
                                           UpdateStageAssignmentRequest request) {
        groupService.requireMember(groupId, userId);
        Order order = requireById(groupId, orderId);
        requireIndividual(order);
        StageAssignment assignment = order.getStageAssignments().stream()
                .filter(s -> s.getStageKey().equals(stageKey)).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Stage not found on this order"));
        if (request.assignedCreatorId() != null && !request.assignedCreatorId().equals(assignment.getAssignedCreatorId())) {
            appendChangeLog(orderId, log -> log.getAssigneeChangeHistory().add(OrderChangeLog.AssigneeChange.builder()
                    .stageKey(stageKey).oldValue(assignment.getAssignedCreatorId()).newValue(request.assignedCreatorId())
                    .changedByCreatorId(userId).changeTimestamp(clock.instant()).build()));
            assignment.setAssignedCreatorId(request.assignedCreatorId());
        }
        assignment.setUnitsCompleted(Math.max(0, Math.min(1, request.unitsCompleted())));
        assignment.setLastUpdatedAt(clock.instant());
        order.setUpdatedAt(clock.instant());
        return view(repository.save(order), userId);
    }

    // ---- Bulk stage progress ----

    public OrderView updateBulkStageProgress(String groupId, String userId, String orderId, String stageKey,
                                             UpdateBulkStageProgressRequest request) {
        groupService.requireMember(groupId, userId);
        Order order = requireById(groupId, orderId);
        requireBulk(order);
        Order.StageProgress progress = order.getBulkDetails().getStageProgress().stream()
                .filter(p -> p.getStageKey().equals(stageKey)).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Stage not found, or it isn't batch-tracked for this business"));
        progress.setUnitsCompleted(Math.max(0, Math.min(progress.getTotalUnits(), request.unitsCompleted())));
        order.setUpdatedAt(clock.instant());
        return view(repository.save(order), userId);
    }

    public OrderView updateBulkSplitProgress(String groupId, String userId, String orderId,
                                             UpdateBulkSplitProgressRequest request) {
        groupService.requireMember(groupId, userId);
        Order order = requireById(groupId, orderId);
        requireBulk(order);
        Variant variant = order.getBulkDetails().getVariants().stream()
                .filter(v -> v.getVariantId().equals(request.variantId())).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Variant not found"));
        SplitLine split = variant.getSplitAllocation().stream()
                .filter(s -> s.getCreatorId().equals(request.creatorId())).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "This creator has no split on this variant"));
        Order.StageProgressEntry entry = split.getStageProgress().stream()
                .filter(e -> e.getStageKey().equals(request.stageKey())).findFirst()
                .orElseGet(() -> {
                    Order.StageProgressEntry created = Order.StageProgressEntry.builder()
                            .stageKey(request.stageKey()).unitsCompleted(0).build();
                    split.getStageProgress().add(created);
                    return created;
                });
        entry.setUnitsCompleted(Math.max(0, Math.min(split.getQuantityAssigned(), request.unitsCompleted())));
        order.setUpdatedAt(clock.instant());
        return view(repository.save(order), userId);
    }

    // ---- Payments ----

    public OrderView addPayment(String groupId, String userId, String orderId, AddPaymentRequest request) {
        groupService.requireMember(groupId, userId);
        Order order = requireById(groupId, orderId);
        order.getPayments().add(Order.PaymentEntry.builder()
                .paymentId(java.util.UUID.randomUUID().toString())
                .type(request.type())
                .amount(EncryptedString.of(Double.toString(request.amount())))
                .date(request.date() == null ? clock.instant() : request.date())
                .mode(EncryptedString.of(request.mode()))
                .note(EncryptedString.of(request.note()))
                .build());
        double finalCost = order.getOrderType() == OrderType.INDIVIDUAL
                ? (order.getCostEstimate() == null ? 0 : order.getCostEstimate().getFinalCost())
                : (order.getBulkDetails() == null ? 0 : order.getBulkDetails().getTotalFinalCost());
        PaymentStatusHolder oldStatus = new PaymentStatusHolder(order.getPaymentStatus());
        order.setPaymentStatus(calculator.derivePaymentStatus(order.getPayments(), finalCost));
        if (oldStatus.value != order.getPaymentStatus()) {
            appendChangeLog(orderId, log -> log.getPaymentStatusChangeHistory().add(
                    OrderChangeLog.PaymentStatusChange.builder().oldValue(oldStatus.value)
                            .newValue(order.getPaymentStatus()).changedByCreatorId(userId)
                            .changeTimestamp(clock.instant()).build()));
        }
        order.setUpdatedAt(clock.instant());
        return view(repository.save(order), userId);
    }

    private record PaymentStatusHolder(com.backlogtracker.ordertracker.order.domain.PaymentStatus value) {
    }

    // ---- Time tracking ----

    private static final double TIME_QUARTER_STEP_EPSILON = 1e-9;

    /** RESEARCH is always whole-order; CRAFTING/ASSEMBLY go on the order itself for an
     *  INDIVIDUAL order, or on the named variant for a BULK order — matching exactly how
     *  those estimates are already split (design decision, see {@link Order#timeLogEntries}). */
    public OrderView addTimeLogEntry(String groupId, String userId, String orderId, AddTimeLogEntryRequest request) {
        groupService.requireMember(groupId, userId);
        Order order = requireById(groupId, orderId);
        String loggedByCreatorId = creatorService.require(groupId, userId).getId();
        double hours = requireQuarterStepHours(request.hours());

        Order.TimeLogEntry entry = Order.TimeLogEntry.builder()
                .entryId(java.util.UUID.randomUUID().toString())
                .stage(request.stage())
                .hours(hours)
                .date(request.date() == null ? clock.instant() : request.date())
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
        order.setUpdatedAt(clock.instant());
        return view(repository.save(order), userId);
    }

    private static Order.Component requireComponent(List<Order.Component> components, String componentId) {
        return components.stream().filter(c -> c.getComponentId().equals(componentId)).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Component not found"));
    }

    public OrderView removeTimeLogEntry(String groupId, String userId, String orderId, String entryId) {
        groupService.requireMember(groupId, userId);
        Order order = requireById(groupId, orderId);
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
        if (!removed) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Time log entry not found");
        }
        order.setUpdatedAt(clock.instant());
        return view(repository.save(order), userId);
    }

    private static double requireQuarterStepHours(double hours) {
        if (hours <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "hours must be greater than zero");
        }
        double quarters = hours * 4;
        if (Math.abs(quarters - Math.round(quarters)) > TIME_QUARTER_STEP_EPSILON) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "hours must be in quarter-hour steps (e.g. 0.25, 1.5)");
        }
        return Math.round(quarters) / 4.0;
    }

    // ---- Shipment plan ----

    public OrderView setShipmentPlan(String groupId, String userId, String orderId, ShipmentPlanRequest request) {
        groupService.requireMember(groupId, userId);
        Order order = requireById(groupId, orderId);
        List<Order.ShipmentStop> stops = request.stops().stream().map(s -> Order.ShipmentStop.builder()
                .stopOrder(s.stopOrder())
                .type(s.type())
                .originLocationCode(s.originLocationCode())
                .destinationLocationCode(s.destinationLocationCode())
                .laneId(s.laneId())
                .estimatedCost(s.estimatedCost())
                .estimatedTimeHours(s.estimatedTimeHours())
                .carrier(s.carrier())
                .trackingNumber(EncryptedString.of(s.trackingNumber()))
                .triggerDate(s.triggerDate())
                .build()).collect(Collectors.toList());
        order.setShipmentPlan(Order.ShipmentPlan.builder().stops(stops).build());
        order.setUpdatedAt(clock.instant());
        return view(repository.save(order), userId);
    }

    public OrderView markShipmentStop(String groupId, String userId, String orderId, int stopIndex,
                                      MarkShipmentStopRequest request) {
        groupService.requireMember(groupId, userId);
        Order order = requireById(groupId, orderId);
        List<Order.ShipmentStop> stops = order.getShipmentPlan() == null ? List.of() : order.getShipmentPlan().getStops();
        if (stopIndex < 0 || stopIndex >= stops.size()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Shipment stop not found on this order");
        }
        Order.ShipmentStop stop = stops.get(stopIndex);
        if (request.shippedDate() != null) {
            stop.setShippedDate(request.shippedDate());
        }
        if (request.deliveredConfirmed() != null) {
            stop.setDeliveredConfirmed(request.deliveredConfirmed());
        }
        order.setUpdatedAt(clock.instant());
        return view(repository.save(order), userId);
    }

    // =====================================================================================
    // Helpers
    // =====================================================================================

    private Variant buildVariant(String groupId, String userId, CreateOrderRequest.VariantInput input,
                                 BusinessConfig cfg) {
        Order.Packaging packaging = buildPackaging(groupId, userId, input.packagingPresetId(), input.itemizedPackaging());
        Variant variant = Variant.builder()
                .variantId(input.variantId() == null || input.variantId().isBlank()
                        ? java.util.UUID.randomUUID().toString() : input.variantId())
                .label(input.label())
                .quantity(input.quantity())
                .mandatoryItems(toMandatoryItems(input.mandatoryItems()))
                .addOns(toLineItems(input.addOns()))
                .components(toComponents(groupId, userId, input.components(), List.of()))
                .packaging(packaging)
                .craftingTimeHours(input.craftingTimeHours())
                .assemblyTimeHours(input.assemblyTimeHours())
                .splitAllocation(input.splitAllocation() == null ? new ArrayList<>() : input.splitAllocation().stream()
                        .map(s -> SplitLine.builder().creatorId(s.creatorId()).quantityAssigned(s.quantityAssigned())
                                .stageProgress(new ArrayList<>()).build())
                        .collect(Collectors.toCollection(ArrayList::new)))
                .build();
        return calculator.priceVariant(variant, cfg.getOverheadPercentage(), cfg.getProfitMarginPercentage());
    }

    private void recomputeBulkTotals(Order.BulkDetails details, BusinessConfig cfg, Instant orderReceivedDate,
                                     double researchTimeHours) {
        details.setTotalQuantity(details.getVariants().stream().mapToInt(Variant::getQuantity).sum());
        details.setTotalFinalCost(details.getVariants().stream().mapToDouble(Variant::getTotalCost).sum());
        details.setTotalTimeHours(details.getVariants().stream().mapToDouble(Variant::getTotalTimeHours).sum());

        List<String> creatorIds = details.getVariants().stream()
                .flatMap(v -> v.getSplitAllocation().stream().map(SplitLine::getCreatorId))
                .distinct().toList();
        List<OrderCalculator.CreatorWorkload> workloads = creatorIds.stream()
                .map(id -> new OrderCalculator.CreatorWorkload(calculator.creatorTotalHours(id, details.getVariants()),
                        creatorService.requireById(cfg.getGroupId(), id).getHoursAvailablePerDay()))
                .toList();
        if (workloads.isEmpty()) {
            details.setComputedDueDate(orderReceivedDate);
            return;
        }
        // research time is a one-time order-level cost, not per-creator — pad the bulk due
        // date with it via the coordinating creator's own pace, the same way the logistics
        // buffer already pads it with flat extra days.
        double researchHoursPerDay = creatorService.requireById(cfg.getGroupId(), details.getCoordinatingCreatorId())
                .getHoursAvailablePerDay();
        int researchDays = (int) calculator.extraDaysFor(researchTimeHours, researchHoursPerDay);
        details.setComputedDueDate(calculator.computeBulkDueDate(orderReceivedDate, workloads,
                details.getLogisticsBufferDays() + researchDays));
    }

    private Order.Packaging buildPackaging(String groupId, String userId, String presetId,
                                           List<CreateOrderRequest.LineItemInput> itemized) {
        List<LineItem> lineItems = toLineItems(itemized);
        if (!lineItems.isEmpty()) {
            return Order.Packaging.builder().itemizedList(lineItems).build();
        }
        if (presetId != null && !presetId.isBlank()) {
            PresetOption preset = masterDataService.requirePackagingPreset(groupId, userId, presetId);
            return Order.Packaging.builder()
                    .tentativePresetId(preset.getId())
                    .presetCost(preset.getEstimatedCost())
                    .presetTimeHours(preset.getEstimatedTimeHours())
                    .itemizedList(List.of())
                    .build();
        }
        return Order.Packaging.builder().itemizedList(List.of()).build();
    }

    private Pattern toPattern(CreateOrderRequest.PatternInput input) {
        if (input == null) {
            return null;
        }
        return Pattern.builder().patternType(input.patternType()).templateName(input.templateName())
                .customPatternNotes(input.customPatternNotes())
                .attachmentUrls(input.attachmentUrls() == null ? List.of() : input.attachmentUrls())
                .recipeSteps(input.recipeSteps() == null ? List.of() : input.recipeSteps())
                .build();
    }

    private List<Order.ResearchItem> toResearchItems(List<CreateOrderRequest.ResearchItemInput> inputs) {
        if (inputs == null) {
            return List.of();
        }
        return inputs.stream().map(i -> Order.ResearchItem.builder().type(i.type()).url(i.url())
                .description(i.description()).build()).toList();
    }

    private List<MandatoryItem> toMandatoryItems(List<CreateOrderRequest.MandatoryItemInput> inputs) {
        if (inputs == null) {
            return List.of();
        }
        return inputs.stream().map(i -> MandatoryItem.builder().kind(i.kind()).value(i.value())
                .quantity(i.quantity()).unitCost(i.unitCost()).notes(i.notes())
                .linkedYarnTypeId(i.linkedYarnTypeId()).linkedNeedleTypeId(i.linkedNeedleTypeId()).build()).toList();
    }

    private List<LineItem> toLineItems(List<CreateOrderRequest.LineItemInput> inputs) {
        if (inputs == null) {
            return List.of();
        }
        return inputs.stream().map(i -> LineItem.builder().name(i.name()).category(i.category())
                .attributes(i.attributes() == null ? java.util.Map.of() : i.attributes()).quantity(i.quantity())
                .unitCost(i.unitCost()).unitTimeHours(i.unitTimeHours()).note(i.note()).build()).toList();
    }

    /** Builds this order/variant's components, snapshotting each referenced template's
     *  label and crafting-time estimate at pick-time (same convention as a packaging
     *  preset) and pricing the result — the caller still needs to fold the returned
     *  components' cost/time into its own overall estimate. A component whose id matches
     *  one in {@code existing} keeps its logged time history across the edit; a new or
     *  re-generated id starts with none, same as a brand-new component. */
    private List<Order.Component> toComponents(String groupId, String userId, List<CreateOrderRequest.ComponentInput> inputs,
                                                List<Order.Component> existing) {
        if (inputs == null) {
            return new ArrayList<>();
        }
        return inputs.stream().map(i -> {
            var template = masterDataService.requireComponentTemplate(groupId, userId, i.templateId());
            String componentId = i.componentId() == null || i.componentId().isBlank()
                    ? java.util.UUID.randomUUID().toString() : i.componentId();
            List<Order.TimeLogEntry> carriedOverEntries = existing.stream()
                    .filter(e -> e.getComponentId().equals(componentId)).findFirst()
                    .map(e -> new ArrayList<>(e.getTimeLogEntries())).orElseGet(ArrayList::new);
            Order.Component component = Order.Component.builder()
                    .componentId(componentId)
                    .templateId(i.templateId())
                    .label(template.getLabel())
                    .templateCraftingTimeHours(template.getBaseCraftingTimeHours())
                    .quantity(i.quantity())
                    .mandatoryItems(toMandatoryItems(i.mandatoryItems()))
                    .addOns(toLineItems(i.addOns()))
                    .craftingTimeHours(i.craftingTimeHours())
                    .timeLogEntries(carriedOverEntries)
                    .build();
            return calculator.priceComponent(component);
        }).collect(Collectors.toCollection(ArrayList::new));
    }

    private boolean sameAllocation(List<SplitLine> a, List<SplitLine> b) {
        if (a.size() != b.size()) {
            return false;
        }
        for (SplitLine sa : a) {
            boolean matched = b.stream().anyMatch(sb -> sb.getCreatorId().equals(sa.getCreatorId())
                    && sb.getQuantityAssigned() == sa.getQuantityAssigned());
            if (!matched) {
                return false;
            }
        }
        return true;
    }

    private List<SplitLine> mergeProgress(List<SplitLine> oldLines, List<SplitLine> newLines) {
        for (SplitLine nl : newLines) {
            oldLines.stream().filter(ol -> ol.getCreatorId().equals(nl.getCreatorId())).findFirst()
                    .ifPresent(ol -> {
                        // same shrink-below-what's-already-done risk as the batch-tracked
                        // totalUnits resize above, one level down: this creator's own
                        // quantityAssigned may have shrunk on this edit.
                        List<Order.StageProgressEntry> carried = ol.getStageProgress();
                        carried.forEach(sp -> sp.setUnitsCompleted(
                                Math.min(sp.getUnitsCompleted(), nl.getQuantityAssigned())));
                        nl.setStageProgress(carried);
                    });
        }
        return newLines;
    }

    private void logDeliveryDateChange(String orderId, Instant oldValue, Instant newValue, String userId) {
        appendChangeLog(orderId, log -> log.getDeliveryDateChangeHistory().add(OrderChangeLog.DateChange.builder()
                .oldValue(oldValue).newValue(newValue).changedByCreatorId(userId).changeTimestamp(clock.instant())
                .build()));
    }

    private void logQuantityChange(String orderId, double oldValue, double newValue, String variantId, String userId) {
        appendChangeLog(orderId, log -> log.getQuantityChangeHistory().add(OrderChangeLog.QuantityChange.builder()
                .oldValue((int) oldValue).newValue((int) newValue).variantId(variantId).changedByCreatorId(userId)
                .changeTimestamp(clock.instant()).build()));
    }

    private void logSplitReallocation(String orderId, String variantId, List<SplitLine> oldAllocation,
                                      List<SplitLine> newAllocation, String userId) {
        appendChangeLog(orderId, log -> log.getSplitReallocationHistory().add(
                OrderChangeLog.SplitReallocationChange.builder().variantId(variantId).oldAllocation(oldAllocation)
                        .newAllocation(newAllocation).changedByCreatorId(userId).changeTimestamp(clock.instant())
                        .build()));
    }

    private void appendChangeLog(String orderId, java.util.function.Consumer<OrderChangeLog> mutator) {
        OrderChangeLog log = changeLogRepository.findByOrderId(orderId)
                .orElseGet(() -> OrderChangeLog.builder().orderId(orderId).build());
        mutator.accept(log);
        changeLogRepository.save(log);
    }

    private void requireIndividual(Order order) {
        if (order.getOrderType() != OrderType.INDIVIDUAL) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This endpoint is for individual orders only");
        }
    }

    private void requireBulk(Order order) {
        if (order.getOrderType() != OrderType.BULK) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This endpoint is for bulk orders only");
        }
    }

    private OrderView view(Order order, String userId) {
        return view(order, userId, businessConfigService.get(order.getGroupId(), userId));
    }

    /** Same as {@link #view(Order, String)}, but for a caller that's already fetched
     *  {@code cfg} once for a whole list of orders (e.g. {@link #all} / {@link #myWork})
     *  instead of once per order — {@code BusinessConfig} is identical for every order in
     *  the same group, so re-fetching it per order in a loop is a pure N+1 query cost. */
    private OrderView view(Order order, String userId, BusinessConfig cfg) {
        double completion = order.getOrderType() == OrderType.INDIVIDUAL
                ? calculator.individualCompletionFraction(order.getStageAssignments()) * 100
                : calculator.bulkCompletionFraction(order.getBulkDetails(), cfg.getWorkStages()) * 100;
        double finalCost = order.getOrderType() == OrderType.INDIVIDUAL
                ? (order.getCostEstimate() == null ? 0 : order.getCostEstimate().getFinalCost())
                : (order.getBulkDetails() == null ? 0 : order.getBulkDetails().getTotalFinalCost());
        double netPaid = calculator.netPaid(order.getPayments());
        double balance = calculator.balanceAmount(order.getPayments(), finalCost);
        return OrderView.of(order, completion, netPaid, balance);
    }

    private Order requireById(String groupId, String orderId) {
        Order order = repository.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
        if (!groupId.equals(order.getGroupId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found");
        }
        return order;
    }
}
