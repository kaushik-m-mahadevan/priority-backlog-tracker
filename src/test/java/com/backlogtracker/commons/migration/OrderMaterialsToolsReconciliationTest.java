package com.backlogtracker.commons.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.backlogtracker.ordertracker.master.domain.BusinessConfig;
import com.backlogtracker.ordertracker.master.repository.BusinessConfigRepository;
import com.backlogtracker.ordertracker.order.domain.Order;
import com.backlogtracker.ordertracker.order.domain.Order.MandatoryItem;
import com.backlogtracker.ordertracker.order.domain.Order.ToolUsage;
import com.backlogtracker.ordertracker.order.domain.Order.Variant;
import com.backlogtracker.ordertracker.order.domain.OrderType;
import com.backlogtracker.ordertracker.order.repository.OrderRepository;

@SpringBootTest
class OrderMaterialsToolsReconciliationTest {

    @Autowired OrderMaterialsToolsReconciliation reconciliation;
    @Autowired OrderRepository orders;
    @Autowired BusinessConfigRepository businessConfigs;

    /** Reproduces the exact reported defect: "needle" was captured into an order's
     *  mandatoryItems list before its type was ever flagged a tool in Business Settings.
     *  Flipping the flag afterward must not leave the order stuck showing it under
     *  Materials forever. */
    @Test
    void movesAMisclassifiedMaterialIntoToolsOnceItsTypeIsFlaggedATool() {
        String groupId = "recon-test-group-" + System.nanoTime();
        businessConfigs.save(BusinessConfig.builder()
                .groupId(groupId)
                .mandatoryItemTypes(List.of(
                        new BusinessConfig.MandatoryItemType("wool", "Wool", null, false),
                        new BusinessConfig.MandatoryItemType("needle", "Needle", null, true)))
                .build());

        Order order = Order.builder()
                .groupId(groupId)
                .orderType(OrderType.INDIVIDUAL)
                .mandatoryItems(new java.util.ArrayList<>(List.of(
                        MandatoryItem.builder().itemKey("wool").value("Cream").quantity(2).unitCost(50).build(),
                        MandatoryItem.builder().itemKey("needle").value("4mm").quantity(1).unitCost(30).notes("edging").build())))
                .tools(new java.util.ArrayList<>())
                .build();
        order = orders.save(order);

        reconciliation.run(null);

        Order fixed = orders.findById(order.getId()).orElseThrow();
        assertThat(fixed.getMandatoryItems()).extracting(MandatoryItem::getItemKey).containsExactly("wool");
        assertThat(fixed.getTools()).extracting(ToolUsage::getItemKey).containsExactly("needle");
        assertThat(fixed.getTools().get(0).getValue()).isEqualTo("4mm");
        assertThat(fixed.getTools().get(0).getNotes()).isEqualTo("edging");

        orders.deleteById(fixed.getId());
        businessConfigs.deleteById(groupId);
    }

    @Test
    void reconcilesEachBulkVariantIndependently() {
        String groupId = "recon-test-group-" + System.nanoTime();
        businessConfigs.save(BusinessConfig.builder()
                .groupId(groupId)
                .mandatoryItemTypes(List.of(new BusinessConfig.MandatoryItemType("hook", "Hook", null, true)))
                .build());

        Variant variant = Variant.builder()
                .variantId("v1")
                .label("Blue")
                .quantity(1)
                .mandatoryItems(new java.util.ArrayList<>(List.of(
                        MandatoryItem.builder().itemKey("hook").value("5mm").quantity(1).unitCost(10).build())))
                .tools(new java.util.ArrayList<>())
                .build();
        Order order = Order.builder()
                .groupId(groupId)
                .orderType(OrderType.BULK)
                .bulkDetails(Order.BulkDetails.builder().variants(new java.util.ArrayList<>(List.of(variant))).build())
                .build();
        order = orders.save(order);

        reconciliation.run(null);

        Order fixed = orders.findById(order.getId()).orElseThrow();
        Variant fixedVariant = fixed.getBulkDetails().getVariants().get(0);
        assertThat(fixedVariant.getMandatoryItems()).isEmpty();
        assertThat(fixedVariant.getTools()).extracting(ToolUsage::getItemKey).containsExactly("hook");

        orders.deleteById(fixed.getId());
        businessConfigs.deleteById(groupId);
    }

    @Test
    void isIdempotentAndLeavesAlreadyCorrectOrdersUntouched() {
        String groupId = "recon-test-group-" + System.nanoTime();
        businessConfigs.save(BusinessConfig.builder()
                .groupId(groupId)
                .mandatoryItemTypes(List.of(
                        new BusinessConfig.MandatoryItemType("wool", "Wool", null, false),
                        new BusinessConfig.MandatoryItemType("needle", "Needle", null, true)))
                .build());

        Order order = Order.builder()
                .groupId(groupId)
                .orderType(OrderType.INDIVIDUAL)
                .mandatoryItems(new java.util.ArrayList<>(List.of(
                        MandatoryItem.builder().itemKey("wool").value("Cream").quantity(2).unitCost(50).build())))
                .tools(new java.util.ArrayList<>(List.of(
                        ToolUsage.builder().itemKey("needle").value("4mm").build())))
                .build();
        order = orders.save(order);

        reconciliation.run(null);
        reconciliation.run(null);

        Order stillCorrect = orders.findById(order.getId()).orElseThrow();
        assertThat(stillCorrect.getMandatoryItems()).extracting(MandatoryItem::getItemKey).containsExactly("wool");
        assertThat(stillCorrect.getTools()).extracting(ToolUsage::getItemKey).containsExactly("needle");

        orders.deleteById(stillCorrect.getId());
        businessConfigs.deleteById(groupId);
    }
}
