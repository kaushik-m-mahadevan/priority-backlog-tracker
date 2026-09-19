package com.backlogtracker.commons.web;

/** Shared page/size clamping for any paginated list endpoint — page floors at 0, size clamps
 *  to [1, 200]. Previously duplicated verbatim in {@code ItemQueryService} and
 *  {@code ArchiveController}. */
public final class Pagination {

    private static final int MAX_SIZE = 200;

    private Pagination() {
    }

    public static int page(int page) {
        return Math.max(0, page);
    }

    public static int size(int size) {
        return Math.min(Math.max(1, size), MAX_SIZE);
    }
}
