package com.backlogtracker.materialinventory.needle.dto;

import com.backlogtracker.materialinventory.needle.domain.NeedleKind;
import com.backlogtracker.materialinventory.needle.domain.NeedleType;

public record NeedleTypeView(String id, NeedleKind kind, String size, String notes) {

    public static NeedleTypeView of(NeedleType n) {
        return new NeedleTypeView(n.getId(), n.getKind(), n.getSize(), n.getNotes());
    }
}
