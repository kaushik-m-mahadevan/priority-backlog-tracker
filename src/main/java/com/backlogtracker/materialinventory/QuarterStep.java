package com.backlogtracker.materialinventory;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Yarn quantities are tracked to the nearest quarter-skein everywhere in this applet — both
 *  {@code InventoryService} and {@code TransferRequestService} used to duplicate this exact
 *  rounding/validation verbatim. */
public final class QuarterStep {

    public static final double EPSILON = 1e-9;

    private QuarterStep() {
    }

    /** Rounds to the nearest quarter and rejects anything further off than {@link #EPSILON}
     *  from a real quarter step. Callers that also need to reject negative quantities do so
     *  themselves before calling this — not every caller wants that guard. */
    public static double require(double quantity) {
        double quarters = quantity * 4;
        if (Math.abs(quarters - Math.round(quarters)) > EPSILON) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "quantity must be in quarter-skein steps (e.g. 0.25, 1.5)");
        }
        return Math.round(quarters) / 4.0;
    }
}
