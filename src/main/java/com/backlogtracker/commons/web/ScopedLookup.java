package com.backlogtracker.commons.web;

import java.util.Optional;
import java.util.function.Function;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** The "find by id, 404 if absent, 404-again (not 403) if it belongs to a different group"
 *  shape (bdup-8) — repeated verbatim across a dozen services (ApprovalService,
 *  OrderService, CustomerService, LedgerEntryService, ColorwayService, YarnTypeService,
 *  NeedleTypeService, OrderFinalizationService, TransferRequestService, ...). Returning
 *  404 rather than 403 for a wrong-group id is deliberate everywhere this appears — it
 *  avoids confirming the id exists at all to a caller who isn't a member of its group. */
public final class ScopedLookup {

    private ScopedLookup() {
    }

    public static <T> T requireInGroup(Optional<T> found, Function<T, String> groupIdOf, String groupId, String notFoundMessage) {
        T entity = found.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, notFoundMessage));
        if (!groupId.equals(groupIdOf.apply(entity))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, notFoundMessage);
        }
        return entity;
    }
}
