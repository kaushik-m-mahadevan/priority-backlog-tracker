package com.backlogtracker.backlogtracker.search;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import com.backlogtracker.commons.web.PageResponse;
import com.backlogtracker.commons.web.Pagination;
import com.backlogtracker.backlogtracker.item.domain.Item;
import com.backlogtracker.backlogtracker.item.domain.ItemStatus;

import lombok.RequiredArgsConstructor;

/**
 * Server-side search &amp; filtering for one group's live item list: title contains, plus
 * combinable owner / category / priority / status, paginated. Membership is checked by
 * the caller before this runs.
 */
@Service
@RequiredArgsConstructor
public class ItemQueryService {

    private static final List<ItemStatus> LIVE = List.of(ItemStatus.BACKLOG, ItemStatus.IN_PROGRESS);

    private final MongoOperations mongo;

    public PageResponse<Item> search(String groupId, String q, String owner, String category,
                                     String priority, ItemStatus status, int page, int size) {
        int p = Pagination.page(page);
        int s = Pagination.size(size);

        List<Criteria> and = new ArrayList<>();
        and.add(Criteria.where("groupId").is(groupId));
        and.add(status != null
                ? Criteria.where("status").is(status)
                : Criteria.where("status").in(LIVE));
        if (has(q)) {
            and.add(Criteria.where("title").regex(Pattern.quote(q.trim()), "i"));
        }
        if (has(owner)) {
            and.add(Criteria.where("ownerId").is(owner.trim()));
        }
        if (has(category)) {
            and.add(Criteria.where("category").is(category.trim()));
        }
        if (has(priority)) {
            and.add(Criteria.where("priority").is(priority.trim()));
        }

        Query query = new Query(new Criteria().andOperator(and.toArray(Criteria[]::new)));
        long total = mongo.count(query, Item.class);
        query.with(PageRequest.of(p, s, Sort.by(Sort.Direction.ASC, "createdAt")));
        return PageResponse.of(mongo.find(query, Item.class), p, s, total);
    }

    private static boolean has(String v) {
        return v != null && !v.isBlank();
    }
}
