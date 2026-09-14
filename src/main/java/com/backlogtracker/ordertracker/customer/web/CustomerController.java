package com.backlogtracker.ordertracker.customer.web;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.backlogtracker.commons.security.AuthUser;
import com.backlogtracker.commons.security.RequiresUser;
import com.backlogtracker.ordertracker.customer.dto.CustomerView;
import com.backlogtracker.ordertracker.customer.dto.UpsertCustomerRequest;
import com.backlogtracker.ordertracker.customer.service.CustomerService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/ordertracker/groups/{groupId}/customers")
@RequiresUser
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService customerService;

    @GetMapping
    public List<CustomerView> all(@PathVariable String groupId, @AuthenticationPrincipal AuthUser actor) {
        return customerService.all(groupId, actor.id());
    }

    @GetMapping("/{customerId}")
    public CustomerView get(@PathVariable String groupId, @PathVariable String customerId,
                            @AuthenticationPrincipal AuthUser actor) {
        return customerService.get(groupId, actor.id(), customerId);
    }

    /** Exact-match lookup by email/IG handle/phone (blind-indexed — see CustomerService) so
     *  the order form can offer "use this existing customer" instead of creating a
     *  duplicate. Any combination of the three params may be supplied. */
    @GetMapping("/search")
    public List<CustomerView> search(@PathVariable String groupId,
                                     @RequestParam(required = false) String email,
                                     @RequestParam(required = false) String instagramHandle,
                                     @RequestParam(required = false) String contactNumber,
                                     @AuthenticationPrincipal AuthUser actor) {
        return customerService.search(groupId, actor.id(), email, instagramHandle, contactNumber);
    }

    @PostMapping
    public CustomerView create(@PathVariable String groupId, @RequestBody UpsertCustomerRequest request,
                               @AuthenticationPrincipal AuthUser actor) {
        return customerService.create(groupId, actor.id(), request);
    }

    @PutMapping("/{customerId}")
    public CustomerView update(@PathVariable String groupId, @PathVariable String customerId,
                               @RequestBody UpsertCustomerRequest request, @AuthenticationPrincipal AuthUser actor) {
        return customerService.update(groupId, actor.id(), customerId, request);
    }
}
