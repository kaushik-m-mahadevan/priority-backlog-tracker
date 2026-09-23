package com.backlogtracker.commons.search;

/**
 * One cross-applet search hit (ad-5). {@code category} is a short applet-scoped label for
 * grouping in the UI (e.g. "Order", "Customer", "Yarn type"); {@code path} is the frontend
 * route to navigate to on click — each applet's own {@link Searchable} provider knows its
 * own routes, so this stays a dumb data carrier with no frontend-routing knowledge here.
 */
public record SearchResult(String category, String id, String title, String subtitle, String path) {
}
