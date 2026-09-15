package com.backlogtracker.materialinventory.yarn.dto;

import com.backlogtracker.materialinventory.yarn.domain.YarnType;

public record YarnTypeView(String id, String brand, String thickness, String colour, String notes) {

    public static YarnTypeView of(YarnType y) {
        return new YarnTypeView(y.getId(), y.getBrand(), y.getThickness(), y.getColour(), y.getNotes());
    }
}
