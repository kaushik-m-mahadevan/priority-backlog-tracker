package com.backlogtracker.ordertracker.order.validation;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import com.backlogtracker.ordertracker.order.dto.CreateOrderRequest.SplitLineInput;
import com.backlogtracker.ordertracker.order.dto.CreateOrderRequest.VariantInput;

/**
 * Business-rule checks every incoming order-shaped request is subject to, kept out of
 * {@code OrderService} in one place per rule so the service reads as "what happens" and
 * this reads as "what's allowed" — a rule gets added here as its own named method, not
 * folded into the builder logic where the next person would have to go hunting for it.
 *
 * <p>The frontend enforces the same rules for instant feedback (see
 * {@code OrderFormFields.tsx}'s {@code validateSplits}), but this is the actual guard —
 * these rules run on every create/update regardless of what client sent the request.
 */
@Component
public class OrderBusinessRules {

    /** Every rule that applies to one bulk-order variant, run once per variant on both
     *  order creation and {@code updateBulkDetails}. */
    public void validateVariant(VariantInput variant) {
        requireSplitAllocationSumsToQuantity(variant);
    }

    /** A variant's creator-split quantities must add up to the variant's own quantity —
     *  otherwise completion-percentage and due-date math (both keyed off the declared
     *  quantity, not the sum of splits) silently goes wrong with no error anywhere. */
    private void requireSplitAllocationSumsToQuantity(VariantInput variant) {
        if (variant.splitAllocation() == null || variant.splitAllocation().isEmpty()) {
            return;
        }
        int assigned = variant.splitAllocation().stream().mapToInt(SplitLineInput::quantityAssigned).sum();
        if (assigned != variant.quantity()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "\"" + variant.label() + "\" has a quantity of " + variant.quantity()
                            + " but the creator split adds up to " + assigned + " — they must match.");
        }
    }
}
