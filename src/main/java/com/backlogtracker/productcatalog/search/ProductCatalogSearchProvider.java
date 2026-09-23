package com.backlogtracker.productcatalog.search;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Component;

import com.backlogtracker.commons.group.domain.Group;
import com.backlogtracker.commons.group.service.GroupService;
import com.backlogtracker.commons.search.SearchResult;
import com.backlogtracker.commons.search.Searchable;
import com.backlogtracker.productcatalog.colorway.domain.Colorway;
import com.backlogtracker.productcatalog.colorway.repository.ColorwayRepository;

import lombok.RequiredArgsConstructor;

/** ad-5: Product Catalog's cross-applet search contribution — colorways matched by name or
 *  colour. */
@Component
@RequiredArgsConstructor
public class ProductCatalogSearchProvider implements Searchable {

    private static final int MAX_RESULTS = 8;

    private final ColorwayRepository colorways;
    private final GroupService groupService;

    @Override
    public String appletKey() {
        return Group.APPLET_PRODUCT_CATALOG;
    }

    @Override
    public List<SearchResult> search(String groupId, String userId, String query) {
        groupService.requireMember(groupId, userId);
        String needle = query.trim().toLowerCase(Locale.ROOT);
        List<SearchResult> results = new ArrayList<>();
        for (Colorway c : colorways.findByGroupId(groupId)) {
            boolean matches = (c.getName() != null && c.getName().toLowerCase(Locale.ROOT).contains(needle))
                    || (c.getColour() != null && c.getColour().toLowerCase(Locale.ROOT).contains(needle));
            if (matches) {
                results.add(new SearchResult("Colorway", c.getId(), c.getName() + " — " + c.getColour(),
                        c.isIdeabox() ? "Idea box" : "Catalog", "/productcatalog"));
                if (results.size() >= MAX_RESULTS) {
                    break;
                }
            }
        }
        return results;
    }
}
