package com.backlogtracker.commons.search;

import java.util.List;

/**
 * Implemented by one Spring bean per applet that wants to participate in cross-applet
 * search (ad-5) — keeps {@link CrossAppletSearchService} generic over every applet without
 * ever importing an applet's own domain types (commons.* is the only package any applet may
 * import from; this interface is how the dependency runs the other way for search).
 */
public interface Searchable {

    /** One of the {@code Group.APPLET_*} constants — which applet's groups this provider
     *  can search. */
    String appletKey();

    /** Caller must already be a verified member of {@code groupId} — every implementation
     *  still re-checks membership itself rather than trusting the orchestrator, since a
     *  provider searching a *linked* group needs to run its own applet's own authorization
     *  rules, not the calling applet's. */
    List<SearchResult> search(String groupId, String userId, String query);
}
