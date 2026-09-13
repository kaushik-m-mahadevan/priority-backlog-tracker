package com.backlogtracker.ordertracker.order.service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.backlogtracker.commons.group.service.GroupService;
import com.backlogtracker.ordertracker.customer.service.CustomerService;
import com.backlogtracker.ordertracker.master.domain.BusinessConfig;
import com.backlogtracker.ordertracker.master.domain.Creator;
import com.backlogtracker.ordertracker.master.domain.PresetOption;
import com.backlogtracker.ordertracker.master.service.BusinessConfigService;
import com.backlogtracker.ordertracker.master.service.CreatorService;
import com.backlogtracker.ordertracker.master.service.MasterDataService;
import com.backlogtracker.ordertracker.order.domain.BulkOrder;
import com.backlogtracker.ordertracker.order.domain.BulkVariant;
import com.backlogtracker.ordertracker.order.domain.ChangeLogEntry;
import com.backlogtracker.ordertracker.order.domain.CreatorSplit;
import com.backlogtracker.ordertracker.order.domain.OrderStatus;
import com.backlogtracker.ordertracker.order.domain.Packaging;
import com.backlogtracker.ordertracker.order.domain.Payment;
import com.backlogtracker.ordertracker.order.dto.AddPaymentRequest;
import com.backlogtracker.ordertracker.order.dto.BulkOrderView;
import com.backlogtracker.ordertracker.order.dto.CreateBulkOrderRequest;
import com.backlogtracker.ordertracker.order.dto.UpdateCreatorSplitRequest;
import com.backlogtracker.ordertracker.order.repository.BulkOrderRepository;

import lombok.RequiredArgsConstructor;

/**
 * Bulk-order CRUD and lifecycle (design §8/§9) — variants instead of a single item,
 * quantity split across one or more creators, and a due date driven by whichever assigned
 * creator carries the heaviest load (platform integration decision: unlike individual
 * orders' single-primary-creator rule, this max-of-offsets logic stays specific to bulk
 * orders).
 */
@Service
@RequiredArgsConstructor
public class BulkOrderService {

    private final BulkOrderRepository repository;
    private final GroupService groupService;
    private final CustomerService customerService;
    private final CreatorService creatorService;
    private final BusinessConfigService businessConfigService;
    private final MasterDataService masterDataService;
    private final OrderNumberService orderNumberService;
    private final OrderCalculator calculator;
    private final Clock clock;

    public List<BulkOrderView> all(String groupId, String userId) {
        groupService.requireMember(groupId, userId);
        return repository.findByGroupId(groupId).stream().map(o -> BulkOrderView.of(o, calculator)).toList();
    }

    public BulkOrderView get(String groupId, String userId, String orderId) {
        groupService.requireMember(groupId, userId);
        return BulkOrderView.of(requireById(groupId, orderId), calculator);
    }

    public BulkOrderView create(String groupId, String userId, CreateBulkOrderRequest request) {
        groupService.requireMember(groupId, userId);
        customerService.requireExists(groupId, request.customerId());
        if (request.creatorSplits() == null || request.creatorSplits().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At least one creator split is required");
        }
        BusinessConfig cfg = businessConfigService.get(groupId, userId);

        List<Creator> assignedCreators = request.creatorSplits().stream()
                .map(s -> creatorService.requireById(groupId, s.creatorId()))
                .toList();
        Creator anchorCreator = assignedCreators.get(0);

        Packaging packaging = buildPackaging(groupId, userId, request);
        List<BulkVariant> variants = request.variants() == null ? List.of() : request.variants().stream()
                .map(v -> BulkVariant.builder().label(v.label()).quantity(v.quantity())
                        .mandatoryItems(v.mandatoryItems()).build())
                .collect(Collectors.toList());
        List<CreatorSplit> splits = request.creatorSplits().stream()
                .map(s -> CreatorSplit.builder().creatorId(s.creatorId()).assignedQuantity(s.assignedQuantity())
                        .estimatedHoursPerUnit(s.estimatedHoursPerUnit()).completionFraction(0.0).build())
                .collect(Collectors.toList());

        Instant now = clock.instant();
        String orderNumber = orderNumberService.next(groupId, anchorCreator.getLocationCode(),
                anchorCreator.getCreatorCode(), cfg.getBulkOrderTypeCode());

        List<OrderCalculator.CreatorWorkload> workloads = new ArrayList<>();
        for (int i = 0; i < splits.size(); i++) {
            CreatorSplit s = splits.get(i);
            double hours = s.getAssignedQuantity() * s.getEstimatedHoursPerUnit();
            workloads.add(new OrderCalculator.CreatorWorkload(hours, assignedCreators.get(i).getHoursAvailablePerDay()));
        }

        BulkOrder order = BulkOrder.builder()
                .groupId(groupId)
                .orderNumber(orderNumber)
                .customerId(request.customerId())
                .description(request.description())
                .variants(variants)
                .creatorSplits(splits)
                .packaging(packaging)
                .materialsCost(request.materialsCost())
                .overheadPercentage(cfg.getOverheadPercentage())
                .profitMarginPercentage(cfg.getProfitMarginPercentage())
                .payments(new ArrayList<>())
                .paymentStatus(calculator.derivePaymentStatus(List.of(), 0))
                .status(OrderStatus.RECEIVED)
                .computedDueDate(calculator.computedBulkDueDate(now, workloads))
                .createdAt(now)
                .updatedAt(now)
                .build();

        return BulkOrderView.of(repository.save(order), calculator);
    }

    public BulkOrderView updateCreatorSplitProgress(String groupId, String userId, String orderId, String creatorId,
                                                     UpdateCreatorSplitRequest request) {
        groupService.requireMember(groupId, userId);
        if (request.completionFraction() < 0 || request.completionFraction() > 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "completionFraction must be within [0,1]");
        }
        BulkOrder order = requireById(groupId, orderId);
        CreatorSplit split = order.getCreatorSplits().stream()
                .filter(s -> s.getCreatorId().equals(creatorId))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No such creator split on this order"));
        String oldValue = Double.toString(split.getCompletionFraction());
        split.setCompletionFraction(request.completionFraction());
        logChange(order, userId, "creatorSplit." + creatorId, oldValue, Double.toString(request.completionFraction()));
        order.setUpdatedAt(clock.instant());
        return BulkOrderView.of(repository.save(order), calculator);
    }

    public BulkOrderView addPayment(String groupId, String userId, String orderId, AddPaymentRequest request) {
        groupService.requireMember(groupId, userId);
        BulkOrder order = requireById(groupId, orderId);
        order.getPayments().add(Payment.of(request.amount(), request.mode(), request.note(),
                request.paidAt() == null ? clock.instant() : request.paidAt()));
        double totalCost = calculator.totalCost(order.getMaterialsCost(), order.getPackaging(),
                order.getOverheadPercentage(), order.getProfitMarginPercentage());
        order.setPaymentStatus(calculator.derivePaymentStatus(order.getPayments(), totalCost));
        order.setUpdatedAt(clock.instant());
        return BulkOrderView.of(repository.save(order), calculator);
    }

    private Packaging buildPackaging(String groupId, String userId, CreateBulkOrderRequest request) {
        List<Packaging.LineItem> itemized = request.itemizedPackaging() == null ? List.of()
                : request.itemizedPackaging().stream()
                        .map(li -> Packaging.LineItem.builder().label(li.label()).cost(li.cost())
                                .timeHours(li.timeHours()).build())
                        .collect(Collectors.toList());
        if (!itemized.isEmpty()) {
            return Packaging.builder().itemizedList(itemized).build();
        }
        if (request.packagingPresetId() != null) {
            PresetOption preset = masterDataService.requirePackagingPreset(groupId, userId, request.packagingPresetId());
            return Packaging.builder()
                    .presetId(preset.getId())
                    .presetCost(preset.getEstimatedCost())
                    .presetTimeHours(preset.getEstimatedTimeHours())
                    .itemizedList(List.of())
                    .build();
        }
        return Packaging.builder().itemizedList(List.of()).build();
    }

    private void logChange(BulkOrder order, String userId, String field, String oldValue, String newValue) {
        order.getChangeLog().add(ChangeLogEntry.builder()
                .field(field).oldValue(oldValue).newValue(newValue)
                .changedByUserId(userId).changedAt(clock.instant())
                .build());
    }

    private BulkOrder requireById(String groupId, String orderId) {
        BulkOrder order = repository.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Bulk order not found"));
        if (!groupId.equals(order.getGroupId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Bulk order not found");
        }
        return order;
    }
}
