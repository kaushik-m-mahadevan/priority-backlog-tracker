package com.backlogtracker.ordertracker.order.domain;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Either a reference to a group's {@code PresetOption} (id + snapshotted cost/time so a
 * later preset edit doesn't retroactively change past orders) or a custom itemized list.
 * Both cost and time follow the same rule: itemized sum if {@code itemizedList} is
 * non-empty, else the preset's snapshotted values (platform integration decision).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Packaging {

    private String presetId;
    private double presetCost;
    private double presetTimeHours;
    private List<LineItem> itemizedList;

    public double cost() {
        return (itemizedList == null || itemizedList.isEmpty())
                ? presetCost
                : itemizedList.stream().mapToDouble(LineItem::getCost).sum();
    }

    public double timeHours() {
        return (itemizedList == null || itemizedList.isEmpty())
                ? presetTimeHours
                : itemizedList.stream().mapToDouble(LineItem::getTimeHours).sum();
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LineItem {
        private String label;
        private double cost;
        private double timeHours;
    }
}
