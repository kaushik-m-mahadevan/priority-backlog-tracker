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
import com.backlogtracker.ordertracker.order.domain.Order;
import com.backlogtracker.ordertracker.order.domain.Packaging;
import com.backlogtracker.ordertracker.order.domain.Payment;
import com.backlogtracker.ordertracker.order.domain.StageProgress;
import com.backlogtracker.ordertracker.order.dto.AddPaymentRequest;
import com.backlogtracker.ordertracker.order.dto.CreateOrderRequest;
import com.backlogtracker.ordertracker.order.dto.OrderView;
import com.backlogtracker.ordertracker.order.dto.UpdateStageRequest;
import com.backlogtracker.ordertracker.order.repository.OrderRepository;

import lombok.RequiredArgsConstructor;

/**
 * Individual-order CRUD and lifecycle (design §5/§6/§10). Bulk orders (§8/§9) are a
 * separate aggregate, not handled here.
 */
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository repository;
    private final GroupService groupService;
    private final CustomerService customerService;
    private final CreatorService creatorService;
    private final BusinessConfigService businessConfigService;
    private final MasterDataService masterDataService;
    private final OrderNumberService orderNumberService;
    private final OrderCalculator calculator;
    private final Clock clock;

    public List<OrderView> all(String groupId, String userId) {
        groupService.requireMember(groupId, userId);
        return repository.findByGroupId(groupId).stream().map(o -> OrderView.of(o, calculator)).toList();
    }

    public OrderView get(String groupId, String userId, String orderId) {
        groupService.requireMember(groupId, userId);
        return OrderView.of(requireById(groupId, orderId), calculator);
    }

    public OrderView create(String groupId, String userId, CreateOrderRequest request) {
        groupService.requireMember(groupId, userId);
        customerService.requireExists(groupId, request.customerId());
        Creator primaryCreator = creatorService.requireById(groupId, request.primaryCreatorId());
        BusinessConfig cfg = businessConfigService.get(groupId, userId);

        Packaging packaging = buildPackaging(groupId, userId, request);
        List<StageProgress> stages = buildStages(cfg, request);

        Instant now = clock.instant();
        String orderNumber = orderNumberService.next(groupId, primaryCreator.getLocationCode(),
                primaryCreator.getCreatorCode(), cfg.getIndividualOrderTypeCode());

        Order order = Order.builder()
                .groupId(groupId)
                .orderNumber(orderNumber)
                .customerId(request.customerId())
                .primaryCreatorId(request.primaryCreatorId())
                .description(request.description())
                .mandatoryItems(request.mandatoryItems())
                .addOns(request.addOns())
                .packaging(packaging)
                .stageProgress(stages)
                .materialsCost(request.materialsCost())
                .overheadPercentage(cfg.getOverheadPercentage())
                .profitMarginPercentage(cfg.getProfitMarginPercentage())
                .payments(new ArrayList<>())
                .paymentStatus(calculator.derivePaymentStatus(List.of(), 0))
                .status(com.backlogtracker.ordertracker.order.domain.OrderStatus.RECEIVED)
                .computedDueDate(calculator.computedDueDate(now, stages, packaging.timeHours(),
                        primaryCreator.getHoursAvailablePerDay()))
                .createdAt(now)
                .updatedAt(now)
                .build();

        return OrderView.of(repository.save(order), calculator);
    }

    public OrderView updateStageProgress(String groupId, String userId, String orderId, String stageKey,
                                         UpdateStageRequest request) {
        groupService.requireMember(groupId, userId);
        if (request.completionFraction() < 0 || request.completionFraction() > 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "completionFraction must be within [0,1]");
        }
        Order order = requireById(groupId, orderId);
        StageProgress stage = order.getStageProgress().stream()
                .filter(s -> s.getStageKey().equals(stageKey))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Stage not found on this order"));
        stage.setCompletionFraction(request.completionFraction());
        order.setUpdatedAt(clock.instant());
        return OrderView.of(repository.save(order), calculator);
    }

    public OrderView addPayment(String groupId, String userId, String orderId, AddPaymentRequest request) {
        groupService.requireMember(groupId, userId);
        Order order = requireById(groupId, orderId);
        order.getPayments().add(Payment.of(request.amount(), request.mode(), request.note(),
                request.paidAt() == null ? clock.instant() : request.paidAt()));
        double totalCost = calculator.totalCost(order);
        order.setPaymentStatus(calculator.derivePaymentStatus(order.getPayments(), totalCost));
        order.setUpdatedAt(clock.instant());
        return OrderView.of(repository.save(order), calculator);
    }

    private Packaging buildPackaging(String groupId, String userId, CreateOrderRequest request) {
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

    private List<StageProgress> buildStages(BusinessConfig cfg, CreateOrderRequest request) {
        return cfg.getWorkStages().stream().map(stageType -> {
            double estimatedHours = request.stages() == null ? 0 : request.stages().stream()
                    .filter(s -> s.stageKey().equals(stageType.stageKey()))
                    .mapToDouble(CreateOrderRequest.StageInput::estimatedHours)
                    .findFirst().orElse(0);
            String assignee = request.stages() == null ? null : request.stages().stream()
                    .filter(s -> s.stageKey().equals(stageType.stageKey()))
                    .map(CreateOrderRequest.StageInput::assigneeCreatorId)
                    .findFirst().orElse(null);
            return StageProgress.builder()
                    .stageKey(stageType.stageKey())
                    .label(stageType.label())
                    .sequenceOrder(stageType.sequenceOrder())
                    .assigneeCreatorId(assignee)
                    .estimatedHours(estimatedHours)
                    .completionFraction(0.0)
                    .build();
        }).collect(Collectors.toList());
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
