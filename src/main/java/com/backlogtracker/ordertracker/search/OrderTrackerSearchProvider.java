package com.backlogtracker.ordertracker.search;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Component;

import com.backlogtracker.commons.group.domain.Group;
import com.backlogtracker.commons.group.service.GroupService;
import com.backlogtracker.commons.search.SearchResult;
import com.backlogtracker.commons.search.Searchable;
import com.backlogtracker.ordertracker.customer.domain.Customer;
import com.backlogtracker.ordertracker.customer.repository.CustomerRepository;
import com.backlogtracker.ordertracker.order.domain.Order;
import com.backlogtracker.ordertracker.order.repository.OrderRepository;

import lombok.RequiredArgsConstructor;

/** ad-5: Order Tracker's cross-applet search contribution — orders (by number or item
 *  name) and customers (by name; matched by decrypting and comparing in memory, since
 *  Customer.name is encrypted at rest and ciphertext can't support a substring query —
 *  fine at this app's scale, same trade-off already made elsewhere for encrypted fields). */
@Component
@RequiredArgsConstructor
public class OrderTrackerSearchProvider implements Searchable {

    private static final int MAX_RESULTS_PER_KIND = 8;

    private final OrderRepository orders;
    private final CustomerRepository customers;
    private final GroupService groupService;

    @Override
    public String appletKey() {
        return Group.APPLET_ORDER_TRACKER;
    }

    @Override
    public List<SearchResult> search(String groupId, String userId, String query) {
        groupService.requireMember(groupId, userId);
        String needle = query.trim().toLowerCase(Locale.ROOT);
        List<SearchResult> results = new ArrayList<>();

        for (Order o : orders.findByGroupId(groupId)) {
            boolean matches = (o.getOrderNumber() != null && o.getOrderNumber().toLowerCase(Locale.ROOT).contains(needle))
                    || (o.getItemName() != null && o.getItemName().toLowerCase(Locale.ROOT).contains(needle));
            if (matches) {
                results.add(new SearchResult("Order", o.getId(),
                        o.getItemName() != null ? o.getItemName() : o.getOrderNumber(),
                        o.getOrderNumber(), "/ordertracker/orders/" + o.getId()));
                if (results.size() >= MAX_RESULTS_PER_KIND) {
                    break;
                }
            }
        }

        int customerMatches = 0;
        for (Customer c : customers.findByGroupId(groupId)) {
            String name = c.getName() == null ? null : c.getName().value();
            if (name != null && name.toLowerCase(Locale.ROOT).contains(needle)) {
                results.add(new SearchResult("Customer", c.getId(), name,
                        c.getAcquisitionChannel() != null ? c.getAcquisitionChannel().name() : null,
                        "/ordertracker/customers/" + c.getId()));
                if (++customerMatches >= MAX_RESULTS_PER_KIND) {
                    break;
                }
            }
        }
        return results;
    }
}
