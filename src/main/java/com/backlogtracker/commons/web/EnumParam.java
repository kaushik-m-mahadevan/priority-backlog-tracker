package com.backlogtracker.commons.web;

import java.util.Arrays;
import java.util.stream.Collectors;

/** Parses a request string into an enum constant with a friendly 400 message on failure —
 *  previously duplicated three times (twice in {@code ItemController}, once in
 *  {@code ArchiveController}), each hand-listing its enum's allowed values in prose. */
public final class EnumParam {

    private EnumParam() {
    }

    /** @param paramName used in the error message, e.g. "status" or "terminalStatus" */
    public static <E extends Enum<E>> E parse(Class<E> type, String raw, String paramName) {
        try {
            return Enum.valueOf(type, raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            String allowed = Arrays.stream(type.getEnumConstants())
                    .map(Enum::name)
                    .collect(Collectors.joining(", "));
            throw new IllegalArgumentException(
                    paramName + " must be one of [" + allowed + "] (got '" + raw + "')");
        }
    }
}
