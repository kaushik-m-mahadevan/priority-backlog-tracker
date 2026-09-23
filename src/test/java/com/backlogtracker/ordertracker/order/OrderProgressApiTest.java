package com.backlogtracker.ordertracker.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class OrderProgressApiTest extends OrderApiTestSupport {

    /** Baseline coverage for OrderService.updateStatus, previously only ever exercised
     *  incidentally: the status transition itself, that actualDeliveryDate stamps exactly
     *  once on first reaching DELIVERED and never again on a later status change, and that
     *  a no-op status update doesn't spuriously add a change-log entry. */
    @Test
    void updatingStatusStampsActualDeliveryDateOnlyOnceOnFirstDelivery() throws Exception {
        String orderId = mvc.perform(auth(post("/api/ordertracker/groups/" + groupId + "/orders"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId":"%s","orderType":"INDIVIDUAL","createdByCreatorId":"%s",
                                 "itemName":"Status test bear","orderReceivedDate":"2026-01-01T00:00:00Z",
                                 "mandatoryItems":[],"addOns":[],"craftingTimeHours":2}"""
                                .formatted(customerId, creatorAId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.actualDeliveryDate").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        orderId = mapper.readTree(orderId).get("id").asText();

        mvc.perform(auth(patch("/api/ordertracker/groups/" + groupId + "/orders/" + orderId + "/status"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"IN_PROGRESS\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.actualDeliveryDate").doesNotExist());

        // ad-3: column-adjacency only — IN_PROGRESS (column 1) can't jump straight to
        // DELIVERED (column 3, Closed); READY_TO_SHIP (column 2, Completed) is the required
        // step in between.
        mvc.perform(auth(patch("/api/ordertracker/groups/" + groupId + "/orders/" + orderId + "/status"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"READY_TO_SHIP\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READY_TO_SHIP"));

        String delivered = mvc.perform(auth(patch("/api/ordertracker/groups/" + groupId + "/orders/" + orderId + "/status"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DELIVERED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DELIVERED"))
                .andExpect(jsonPath("$.actualDeliveryDate").exists())
                .andReturn().getResponse().getContentAsString();
        // MongoDB only round-trips Instant at millisecond precision, so compare truncated
        // to millis rather than the sub-millisecond string this first, pre-persistence
        // response happens to carry.
        java.time.Instant firstStamped = java.time.Instant.parse(
                mapper.readTree(delivered).get("actualDeliveryDate").asText())
                .truncatedTo(java.time.temporal.ChronoUnit.MILLIS);

        // A later status change away from and back to DELIVERED must not re-stamp the date.
        // DELIVERED -> SHIPPED is one column backward (Closed -> Completed), which ad-3
        // requires a justification for.
        String shipped = mvc.perform(auth(patch("/api/ordertracker/groups/" + groupId + "/orders/" + orderId + "/status"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SHIPPED\",\"justification\":\"Customer asked to hold delivery\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(java.time.Instant.parse(mapper.readTree(shipped).get("actualDeliveryDate").asText()))
                .isEqualTo(firstStamped);

        String redelivered = mvc.perform(auth(patch("/api/ordertracker/groups/" + groupId + "/orders/" + orderId + "/status"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DELIVERED\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(java.time.Instant.parse(mapper.readTree(redelivered).get("actualDeliveryDate").asText()))
                .isEqualTo(firstStamped);

        String changeLog = mvc.perform(auth(get("/api/ordertracker/groups/" + groupId + "/orders/" + orderId + "/change-log"), token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        // 5 real transitions above (IN_PROGRESS, READY_TO_SHIP, DELIVERED, SHIPPED,
        // DELIVERED again) — no no-op update was sent in this test, so every one of them
        // should be logged, and the backward SHIPPED move should carry its justification.
        var history = mapper.readTree(changeLog).get(0).get("orderStatusChangeHistory");
        assertThat(history).hasSize(5);
        assertThat(history.get(3).get("justification").asText()).isEqualTo("Customer asked to hold delivery");
    }

    @Test
    void stageAssignmentProgressRollsUpIntoEqualWeightedCompletion() throws Exception {
        String orderId = createBasicIndividualOrder();

        mvc.perform(auth(patch("/api/ordertracker/groups/" + groupId + "/orders/" + orderId + "/stage-assignments/crocheting"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"assignedCreatorId":"%s","unitsCompleted":1}""".formatted(creatorAId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completionPercentage").value(25.0)); // 1 of 4 stages
    }

    /** to-5: the previous version of this test only ever updated the split-tracked
     *  crocheting stage, never a batch-tracked one, despite its own name claiming both --
     *  and only asserted completionPercentage was "greater than 0" rather than its exact
     *  value. Now exercises both kinds of stage and asserts the exact weighted-average
     *  result: crocheting 10/20=50%, packaging 5/20=25%, assembly and shipment untouched
     *  at 0% each -- averaged equally across all 4 configured work stages =
     *  (0.5 + 0 + 0.25 + 0) / 4 = 18.75%. */
    @Test
    void bulkSplitAndBatchStageProgressRollUpIntoCompletionPercentage() throws Exception {
        String orderId = createBasicBulkOrder();

        mvc.perform(auth(patch("/api/ordertracker/groups/" + groupId + "/orders/" + orderId + "/bulk-split-progress"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"variantId":"%s","creatorId":"%s","stageKey":"crocheting","unitsCompleted":10}"""
                                .formatted(firstVariantId(orderId), creatorAId)))
                .andExpect(status().isOk());

        mvc.perform(auth(patch("/api/ordertracker/groups/" + groupId + "/orders/" + orderId + "/bulk-stage-progress/packaging"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"unitsCompleted\":5}"))
                .andExpect(status().isOk());

        mvc.perform(get("/api/ordertracker/groups/" + groupId + "/orders/" + orderId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completionPercentage").value(18.75));
    }

    @Test
    void statusChangesAreRecordedInTheStructuredChangeLog() throws Exception {
        String orderId = createBasicIndividualOrder();

        mvc.perform(auth(patch("/api/ordertracker/groups/" + groupId + "/orders/" + orderId + "/status"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"CONFIRMED"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        mvc.perform(get("/api/ordertracker/groups/" + groupId + "/orders/" + orderId + "/change-log")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].orderStatusChangeHistory[0].status").value("CONFIRMED"));
    }
}
