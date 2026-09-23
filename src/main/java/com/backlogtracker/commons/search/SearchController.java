package com.backlogtracker.commons.search;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.backlogtracker.commons.security.AuthUser;
import com.backlogtracker.commons.security.RequiresUser;

import lombok.RequiredArgsConstructor;

/** ad-5: single cross-applet search endpoint, generic over whichever applet {@code groupId}
 *  belongs to — see {@link CrossAppletSearchService}. */
@RestController
@RequestMapping("/api/search")
@RequiresUser
@RequiredArgsConstructor
public class SearchController {

    private final CrossAppletSearchService searchService;

    @GetMapping
    public List<SearchResult> search(@RequestParam String groupId, @RequestParam String q,
                                     @AuthenticationPrincipal AuthUser actor) {
        return searchService.search(groupId, actor.id(), q);
    }
}
