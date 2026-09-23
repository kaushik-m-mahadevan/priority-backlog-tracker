package com.backlogtracker.ordertracker.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** ad-3: the Kanban board's column-based, strictly-adjacent-only transition rule, and the
 *  guided cancel flow — both enforced server-side regardless of which UI path (board,
 *  order-detail page) triggered the move. */
class OrderStatusColumnApiTest extends OrderApiTestSupport {

    private String statusUrl(String orderId) {
        return "/api/ordertracker/groups/" + groupId + "/orders/" + orderId + "/status";
    }

    private String cancelUrl(String orderId) {
        return "/api/ordertracker/groups/" + groupId + "/orders/" + orderId + "/cancel";
    }

    @Test
    void sameColumnMoveIsAlwaysFree() throws Exception {
        String orderId = createBasicIndividualOrder(); // starts INQUIRY (Pending column)

        mvc.perform(auth(patch(statusUrl(orderId)), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CONFIRMED\"}")) // still Pending column
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    void oneColumnForwardIsFreeNoJustificationNeeded() throws Exception {
        String orderId = createBasicIndividualOrder();

        mvc.perform(auth(patch(statusUrl(orderId)), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"IN_PROGRESS\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
    }

    @Test
    void oneColumnBackwardWithoutAJustificationIsRejected() throws Exception {
        String orderId = createBasicIndividualOrder();
        mvc.perform(auth(patch(statusUrl(orderId)), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"IN_PROGRESS\"}"))
                .andExpect(status().isOk());

        mvc.perform(auth(patch(statusUrl(orderId)), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CONFIRMED\"}")) // In Progress -> Pending, no reason given
                .andExpect(status().isBadRequest());
    }

    @Test
    void oneColumnBackwardWithAJustificationSucceedsAndIsLogged() throws Exception {
        String orderId = createBasicIndividualOrder();
        mvc.perform(auth(patch(statusUrl(orderId)), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"IN_PROGRESS\"}"))
                .andExpect(status().isOk());

        mvc.perform(auth(patch(statusUrl(orderId)), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CONFIRMED\",\"justification\":\"Customer changed request\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    void skippingAColumnIsRejectedEvenWithAJustification() throws Exception {
        String orderId = createBasicIndividualOrder(); // Pending column

        mvc.perform(auth(patch(statusUrl(orderId)), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"READY_TO_SHIP\"}")) // Pending -> Completed: 2 columns forward
                .andExpect(status().isBadRequest());

        mvc.perform(auth(patch(statusUrl(orderId)), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"READY_TO_SHIP\",\"justification\":\"Doesn't matter\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void movingToCancelledThroughTheOrdinaryStatusEndpointIsRejected() throws Exception {
        String orderId = createBasicIndividualOrder();

        mvc.perform(auth(patch(statusUrl(orderId)), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CANCELLED\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aCancelledOrderCanNoLongerBeMoved() throws Exception {
        String orderId = createBasicIndividualOrder();
        mvc.perform(auth(post(cancelUrl(orderId)), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Customer changed their mind\"}"))
                .andExpect(status().isOk());

        mvc.perform(auth(patch(statusUrl(orderId)), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CONFIRMED\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void cancellingRequiresAReasonAndRecordsAnInformationalLossEstimate() throws Exception {
        String orderId = createBasicIndividualOrder(); // 1 yarn @100 + 1h crafting

        mvc.perform(auth(post(cancelUrl(orderId)), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":\"no reason given\"}"))
                .andExpect(status().isBadRequest());

        String body = mvc.perform(auth(post(cancelUrl(orderId)), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Item damaged\",\"note\":\"Dropped in transit\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancellation.reason").value("Item damaged"))
                .andExpect(jsonPath("$.cancellation.note").value("Dropped in transit"))
                .andExpect(jsonPath("$.cancellation.estimatedMaterialsLoss").value(100.0))
                .andReturn().getResponse().getContentAsString();
        // laborLoss > 0 (1 crafting hour at the seeded default wage) — exact figure isn't
        // this test's concern, just that it was actually computed, not left at zero.
        assertThat(mapper.readTree(body).get("cancellation").get("estimatedLaborLoss").asDouble()).isGreaterThan(0);
    }

    @Test
    void cancellingIsIdempotentlyRejectedOnAnAlreadyCancelledOrder() throws Exception {
        String orderId = createBasicIndividualOrder();
        mvc.perform(auth(post(cancelUrl(orderId)), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Rework needed\"}"))
                .andExpect(status().isOk());

        mvc.perform(auth(post(cancelUrl(orderId)), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Rework needed\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void loggedTimeAndPaymentsSurviveACancellationUntouched() throws Exception {
        String orderId = createBasicIndividualOrder();
        mvc.perform(auth(post("/api/ordertracker/groups/" + groupId + "/orders/" + orderId + "/time-log"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stage\":\"CRAFTING\",\"hours\":0.5}"))
                .andExpect(status().isOk());
        mvc.perform(auth(post("/api/ordertracker/groups/" + groupId + "/orders/" + orderId + "/payments"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"ADVANCE\",\"amount\":50,\"mode\":\"UPI\"}"))
                .andExpect(status().isOk());

        mvc.perform(auth(post(cancelUrl(orderId)), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Customer changed request\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timeLogEntries.length()").value(1))
                .andExpect(jsonPath("$.timeLogEntries[0].hours").value(0.5))
                .andExpect(jsonPath("$.payments.length()").value(1))
                .andExpect(jsonPath("$.payments[0].amount").value(50.0));
    }
}
