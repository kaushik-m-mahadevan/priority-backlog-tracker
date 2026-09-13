package com.backlogtracker.backlogtracker.archive.dto;

import jakarta.validation.constraints.Size;

/** Body for {@code POST /api/items/{id}/archive-requests}. The note is optional. */
public record RaiseArchiveRequest(
        @Size(max = 500) String note) {
}
