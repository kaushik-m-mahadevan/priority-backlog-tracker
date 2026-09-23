package com.backlogtracker.commons.web;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** The "required, trimmed text field" / "optional, trimmed-or-null text field" shape,
 *  previously duplicated verbatim across every business-wide canonical-catalog service
 *  (bdup-2: {@code YarnTypeService}, {@code NeedleTypeService}, {@code ColorwayService}).
 *  Only this trivial, risk-free sliver of those three services' overlap was worth sharing
 *  — their create/update/delete bodies stay separate on purpose, since each one's natural
 *  key and domain fields genuinely differ enough that templating them would trade a modest
 *  per-call-site saving for a real generics-shaped indirection layer. */
public final class RequiredField {

    private RequiredField() {
    }

    public static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " is required");
        }
        return value.trim();
    }

    public static String trimOrNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
