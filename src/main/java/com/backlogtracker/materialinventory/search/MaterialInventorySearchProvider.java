package com.backlogtracker.materialinventory.search;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Component;

import com.backlogtracker.commons.group.domain.Group;
import com.backlogtracker.commons.group.service.GroupService;
import com.backlogtracker.commons.search.SearchResult;
import com.backlogtracker.commons.search.Searchable;
import com.backlogtracker.materialinventory.yarn.domain.YarnType;
import com.backlogtracker.materialinventory.yarn.repository.YarnTypeRepository;

import lombok.RequiredArgsConstructor;

/** ad-5: Material Inventory's cross-applet search contribution — yarn types matched by
 *  brand or colour. */
@Component
@RequiredArgsConstructor
public class MaterialInventorySearchProvider implements Searchable {

    private static final int MAX_RESULTS = 8;

    private final YarnTypeRepository yarnTypes;
    private final GroupService groupService;

    @Override
    public String appletKey() {
        return Group.APPLET_MATERIAL_INVENTORY;
    }

    @Override
    public List<SearchResult> search(String groupId, String userId, String query) {
        groupService.requireMember(groupId, userId);
        String needle = query.trim().toLowerCase(Locale.ROOT);
        List<SearchResult> results = new ArrayList<>();
        for (YarnType y : yarnTypes.findByGroupId(groupId)) {
            boolean matches = (y.getBrand() != null && y.getBrand().toLowerCase(Locale.ROOT).contains(needle))
                    || (y.getColour() != null && y.getColour().toLowerCase(Locale.ROOT).contains(needle));
            if (matches) {
                results.add(new SearchResult("Yarn type", y.getId(),
                        y.getBrand() + " — " + y.getThickness() + ", " + y.getColour(),
                        y.getMaterial(), "/materialinventory"));
                if (results.size() >= MAX_RESULTS) {
                    break;
                }
            }
        }
        return results;
    }
}
