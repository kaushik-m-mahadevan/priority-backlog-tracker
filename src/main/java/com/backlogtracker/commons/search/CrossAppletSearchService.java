package com.backlogtracker.commons.search;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.backlogtracker.commons.group.domain.Group;
import com.backlogtracker.commons.group.service.GroupService;
import com.backlogtracker.commons.link.service.GroupLinkService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * ad-5: one search box scoped to "the current business plus whatever it's linked to" —
 * searches the current group's own applet, then every other applet's group connected to it
 * via {@link GroupLinkService} (skipped where nothing is linked for that applet). Spring
 * autowires every {@link Searchable} bean into {@code providers}, so adding a new applet's
 * search support later is just implementing the interface — no change needed here.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CrossAppletSearchService {

    private static final int MIN_QUERY_LENGTH = 2;

    private final List<Searchable> providers;
    private final GroupService groupService;
    private final GroupLinkService groupLinkService;

    public List<SearchResult> search(String groupId, String userId, String query) {
        if (query == null || query.trim().length() < MIN_QUERY_LENGTH) {
            return List.of();
        }
        Group group = groupService.requireMember(groupId, userId);
        Map<String, Searchable> byAppletKey = providers.stream()
                .collect(java.util.stream.Collectors.toMap(Searchable::appletKey, Function.identity()));

        List<SearchResult> results = new ArrayList<>();
        Searchable own = byAppletKey.get(group.getAppletKey());
        if (own != null) {
            results.addAll(own.search(groupId, userId, query));
        }
        for (Searchable other : providers) {
            if (other.appletKey().equals(group.getAppletKey())) {
                continue;
            }
            groupLinkService.linkedGroupId(groupId, userId, other.appletKey()).ifPresent(linkedGroupId -> {
                try {
                    results.addAll(other.search(linkedGroupId, userId, query));
                } catch (ResponseStatusException e) {
                    // Real, ordinary topology: a linked group's membership isn't required to
                    // match the origin group's (invite-all is best-effort, per
                    // GroupLinkService's own inviteMissingMembers). A caller who isn't a
                    // member of the linked group simply gets no results from that applet,
                    // not a broken search — round 5 review found this crashing the whole
                    // request with an uncaught 403 instead.
                    log.debug("Skipping {} results for group {}: caller {} isn't a member",
                            other.appletKey(), linkedGroupId, userId);
                }
            });
        }
        return results;
    }
}
