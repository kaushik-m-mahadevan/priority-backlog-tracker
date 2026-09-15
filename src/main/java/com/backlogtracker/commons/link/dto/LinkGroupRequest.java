package com.backlogtracker.commons.link.dto;

import jakarta.validation.constraints.NotBlank;

public record LinkGroupRequest(@NotBlank String groupId) {
}
