package com.backlogtracker.materialinventory.yarn.dto;

import com.backlogtracker.materialinventory.yarn.domain.YarnType;

public record YarnTypeView(String id, String brand, String thickness, String colour, String material,
                           Double skeinWeightGrams, Double skeinLengthMeters, String recommendedHookSize,
                           String notes) {

    public static YarnTypeView of(YarnType y) {
        return new YarnTypeView(y.getId(), y.getBrand(), y.getThickness(), y.getColour(), y.getMaterial(),
                y.getSkeinWeightGrams(), y.getSkeinLengthMeters(), y.getRecommendedHookSize(), y.getNotes());
    }
}
