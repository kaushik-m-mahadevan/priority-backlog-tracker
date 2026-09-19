package com.backlogtracker.ordertracker.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class OrderEditingApiTest extends OrderApiTestSupport {

    @Test
    void linkedYarnTypeIdOnAMandatoryItemRoundTripsThroughCreateAndUpdate() throws Exception {
        String body = mvc.perform(auth(post("/api/ordertracker/groups/" + groupId + "/orders"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId":"%s","orderType":"INDIVIDUAL","createdByCreatorId":"%s",
                                 "itemName":"Yarn-linked test item",
                                 "orderReceivedDate":"2026-01-01T00:00:00Z",
                                 "mandatoryItems":[{"kind":"YARN","value":"Red","quantity":1,"unitCost":100,
                                                     "linkedYarnTypeId":"yarn-type-abc"}],
                                 "craftingTimeHours":2}""".formatted(customerId, creatorAId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mandatoryItems[0].linkedYarnTypeId").value("yarn-type-abc"))
                .andReturn().getResponse().getContentAsString();

        // Order Tracker's backend never validates this id against Material Inventory (no
        // cross-applet import) — it's stored and returned opaquely; the frontend does the
        // actual lookup and shortfall comparison.
        assertThat(mapper.readTree(body).get("mandatoryItems").get(0).get("linkedYarnTypeId").asText())
                .isEqualTo("yarn-type-abc");
    }

    @Test
    void individualOrderCanBeFullyEditedAndRecomputesCost() throws Exception {
        String orderId = createBasicIndividualOrder();

        mvc.perform(auth(put("/api/ordertracker/groups/" + groupId + "/orders/" + orderId), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"itemName":"Updated bear","orderReceivedDate":"2026-01-01T00:00:00Z",
                                 "pattern":{"patternType":"TEMPLATE","templateName":"Basic bear"},
                                 "mandatoryItems":[{"kind":"YARN","value":"Cream","quantity":2,"unitCost":50}],
                                 "addOns":[],"craftingTimeHours":2}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itemName").value("Updated bear"))
                .andExpect(jsonPath("$.pattern.templateName").value("Basic bear"))
                .andExpect(jsonPath("$.costEstimate.mandatoryItemsCost").value(100.0)); // 2*50
    }

    @Test
    void individualOrderCustomerCanBeReassigned() throws Exception {
        String orderId = createBasicIndividualOrder();
        String otherCustomerBody = mvc.perform(auth(post("/api/ordertracker/groups/" + groupId + "/customers"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Second Customer","acquisitionChannel":"WALK_IN"}"""))
                .andReturn().getResponse().getContentAsString();
        String otherCustomerId = mapper.readTree(otherCustomerBody).get("id").asText();

        mvc.perform(auth(put("/api/ordertracker/groups/" + groupId + "/orders/" + orderId), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId":"%s","itemName":"Amigurumi bear","orderReceivedDate":"2026-01-01T00:00:00Z",
                                 "mandatoryItems":[{"kind":"YARN","value":"Cream","quantity":1,"unitCost":100}],
                                 "addOns":[],"craftingTimeHours":1}""".formatted(otherCustomerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerId").value(otherCustomerId));
    }

    @Test
    void bulkOrderEnvelopeFieldsAreEditableNotJustVariants() throws Exception {
        String orderId = createBasicBulkOrder();
        String variantId = firstVariantId(orderId);

        mvc.perform(auth(put("/api/ordertracker/groups/" + groupId + "/orders/" + orderId + "/bulk-details"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId":"%s","itemName":"Updated pots name","orderReceivedDate":"2026-01-01T00:00:00Z",
                                 "researchTimeHours":4,
                                 "variants":[{"variantId":"%s","label":"Blue flower","quantity":20,
                                  "mandatoryItems":[{"kind":"YARN","value":"Blue","quantity":1,"unitCost":100}],
                                  "craftingTimeHours":1,
                                  "splitAllocation":[{"creatorId":"%s","quantityAssigned":20}]}],
                                 "coordinatingCreatorId":"%s","logisticsBufferDays":0}"""
                                .formatted(customerId, variantId, creatorAId, creatorAId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itemName").value("Updated pots name"))
                .andExpect(jsonPath("$.researchTimeHours").value(4.0));
    }

    @Test
    void updatingBulkVariantQuantityIsRecordedInQuantityChangeHistory() throws Exception {
        String orderId = createBasicBulkOrder();
        String variantId = firstVariantId(orderId);

        mvc.perform(auth(put("/api/ordertracker/groups/" + groupId + "/orders/" + orderId + "/bulk-details"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId":"%s","itemName":"Mini succulent crochet pots",
                                 "orderReceivedDate":"2026-01-01T00:00:00Z",
                                 "variants":[{"variantId":"%s","label":"Blue flower","quantity":30,
                                  "mandatoryItems":[{"kind":"YARN","value":"Blue","quantity":1,"unitCost":100}],
                                  "craftingTimeHours":1,
                                  "splitAllocation":[{"creatorId":"%s","quantityAssigned":30}]}],
                                 "coordinatingCreatorId":"%s","logisticsBufferDays":0}"""
                                .formatted(customerId, variantId, creatorAId, creatorAId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bulkDetails.totalQuantity").value(30));

        mvc.perform(get("/api/ordertracker/groups/" + groupId + "/orders/" + orderId + "/change-log")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$[0].quantityChangeHistory[0].oldValue").value(20))
                .andExpect(jsonPath("$[0].quantityChangeHistory[0].newValue").value(30));
    }
}
