package com.backlogtracker.backlogtracker.item.service;

import org.springframework.stereotype.Component;

import com.backlogtracker.commons.counter.CounterService;

import lombok.RequiredArgsConstructor;

/** Backlog Tracker's own item-id format ({@code ITM-001}, {@code ITM-002}, ...) — moved out
 *  of the generic {@code commons.counter.CounterService}, which otherwise has no reason to
 *  know about this applet-specific format (design §20 for the underlying atomic sequence). */
@Component
@RequiredArgsConstructor
public class ItemIdGenerator {

    private static final String SHARED_KEY = "shared";

    private final CounterService counters;

    public String next() {
        return "ITM-%03d".formatted(counters.next(SHARED_KEY));
    }
}
