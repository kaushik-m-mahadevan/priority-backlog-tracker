package com.backlogtracker.materialinventory.needle.dto;

import com.backlogtracker.materialinventory.needle.domain.NeedleKind;

public record CreateNeedleTypeRequest(NeedleKind kind, String size, String notes) {
}
