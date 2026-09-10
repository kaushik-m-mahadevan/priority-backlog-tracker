package com.backlogtracker.group.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body for {@code PATCH /api/groups/{id}} — any member may rename the group. */
public record RenameGroupRequest(
        @NotBlank @Size(max = 60) String name) {
}
