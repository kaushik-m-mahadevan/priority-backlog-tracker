package com.backlogtracker.materialinventory.yarn.dto;

import java.time.Instant;
import java.util.List;

import com.backlogtracker.materialinventory.yarn.domain.YarnType;

public record YarnTypeView(String id, String brand, String thickness, String colour, String material,
                           Double skeinWeightGrams, Double skeinLengthMeters, String recommendedHookSize,
                           String notes, Double costPerSkein, List<CostChangeView> costHistory) {

    public record CostChangeView(Double previousCost, Double newCost, Instant changedAt) {
        static CostChangeView of(YarnType.CostChange c) {
            return new CostChangeView(c.getPreviousCost(), c.getNewCost(), c.getChangedAt());
        }
    }

    public static YarnTypeView of(YarnType y) {
        return new YarnTypeView(y.getId(), y.getBrand(), y.getThickness(), y.getColour(), y.getMaterial(),
                y.getSkeinWeightGrams(), y.getSkeinLengthMeters(), y.getRecommendedHookSize(), y.getNotes(),
                y.getCostPerSkein(), y.getCostHistory().stream().map(CostChangeView::of).toList());
    }
}
