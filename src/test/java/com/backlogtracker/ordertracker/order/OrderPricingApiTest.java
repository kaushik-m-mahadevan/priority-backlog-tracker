package com.backlogtracker.ordertracker.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.fasterxml.jackson.databind.JsonNode;

class OrderPricingApiTest extends OrderApiTestSupport {

    // ---- Individual orders ----

    @Test
    void individualOrderComputesCostAndDueDateFromItemizedInputs() throws Exception {
        String body = mvc.perform(auth(post("/api/ordertracker/groups/" + groupId + "/orders"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId":"%s","orderType":"INDIVIDUAL","createdByCreatorId":"%s",
                                 "itemName":"Sunflower amigurumi keychain",
                                 "orderReceivedDate":"2026-01-01T00:00:00Z",
                                 "pattern":{"patternType":"CUSTOM","customPatternNotes":"Kept for recreation","recipeSteps":["Crochet body","Attach clasp"]},
                                 "researchItems":[{"type":"VIDEO","url":"https://example.com/v","description":"reference"}],
                                 "mandatoryItems":[{"kind":"YARN","value":"Yellow + green, 50g","quantity":1,"unitCost":120}],
                                 "addOns":[{"name":"Safety eyes","quantity":1,"unitCost":40,"unitTimeHours":0.2}],
                                 "craftingTimeHours":4}""".formatted(customerId, creatorAId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderNumber").value(org.hamcrest.Matchers.hasLength(14)))
                .andExpect(jsonPath("$.status").value("INQUIRY"))
                .andExpect(jsonPath("$.pattern.customPatternNotes").value("Kept for recreation"))
                .andExpect(jsonPath("$.researchItems[0].type").value("VIDEO"))
                .andExpect(jsonPath("$.pattern.recipeSteps[1]").value("Attach clasp"))
                .andExpect(jsonPath("$.costEstimate.mandatoryItemsCost").value(120.0))
                .andExpect(jsonPath("$.costEstimate.addOnsCost").value(40.0))
                .andReturn().getResponse().getContentAsString();

        JsonNode view = mapper.readTree(body);
        // round 5 pricing redesign: no overhead cost line any more; labor (4h crafting × the
        // business's default ₹100/h wage) folds into gross before the 20% profit margin.
        double materialsAndAddOns = 120 + 40; // no packaging
        double labor = 4.0 * 100.0;
        double gross = materialsAndAddOns + labor;
        double profit = gross * 0.20;
        assertThat(view.get("costEstimate").get("finalCost").asDouble()).isCloseTo(gross + profit,
                org.assertj.core.data.Offset.offset(0.01));
        assertThat(view.get("stageAssignments").size()).isEqualTo(4); // crocheting/assembly/packaging/shipment
    }

    @Test
    void yarnAndNeedleAreBothMandatoryItemsAndBothCanCarryCost() throws Exception {
        mvc.perform(auth(post("/api/ordertracker/groups/" + groupId + "/orders"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId":"%s","orderType":"INDIVIDUAL","createdByCreatorId":"%s",
                                 "itemName":"Amigurumi bear","orderReceivedDate":"2026-01-01T00:00:00Z",
                                 "mandatoryItems":[
                                   {"kind":"YARN","value":"Cream","quantity":1,"unitCost":100},
                                   {"kind":"NEEDLE","value":"4mm hook","quantity":1,"unitCost":20,"notes":"for edging"}
                                 ],
                                 "craftingTimeHours":1}""".formatted(customerId, creatorAId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mandatoryItems.length()").value(2))
                .andExpect(jsonPath("$.mandatoryItems[0].kind").value("YARN"))
                .andExpect(jsonPath("$.mandatoryItems[1].kind").value("NEEDLE"))
                .andExpect(jsonPath("$.mandatoryItems[1].value").value("4mm hook"))
                .andExpect(jsonPath("$.mandatoryItems[1].notes").value("for edging"))
                // both kinds contribute cost now — no more cost-free "tool" concept
                .andExpect(jsonPath("$.costEstimate.mandatoryItemsCost").value(120.0));
    }

    @Test
    void individualOrderTimeFormulaCountsAssemblyAndResearchButNotAddOnTime() throws Exception {
        String body = mvc.perform(auth(post("/api/ordertracker/groups/" + groupId + "/orders"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId":"%s","orderType":"INDIVIDUAL","createdByCreatorId":"%s",
                                 "itemName":"Amigurumi bear","orderReceivedDate":"2026-01-01T00:00:00Z",
                                 "mandatoryItems":[{"kind":"YARN","value":"Cream","quantity":1,"unitCost":100}],
                                 "addOns":[{"name":"Safety eyes","quantity":1,"unitCost":40,"unitTimeHours":5}],
                                 "craftingTimeHours":4,"assemblyTimeHours":2,"researchTimeHours":2}"""
                                .formatted(customerId, creatorAId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.craftingTimeHours").value(4.0))
                .andExpect(jsonPath("$.assemblyTimeHours").value(2.0))
                .andExpect(jsonPath("$.researchTimeHours").value(2.0))
                // creator A is 4h/day (setUp): (4 crochet + 2 assembly + 0 packaging + 2 research) = 8h -> 2 days,
                // NOT 13h -> 4 days, proving the add-on's own unitTimeHours (5h) is ignored.
                .andExpect(jsonPath("$.costEstimate.grossTimeHours").value(8.0))
                .andReturn().getResponse().getContentAsString();

        // round 5 delivery redesign: workDays(2) + the business's default same-city delivery
        // buffer(1) = 3, padded by the default 15% time-overhead and rounded up = 4 days.
        JsonNode view = mapper.readTree(body);
        assertThat(java.time.Instant.parse(view.get("costEstimate").get("computedDueDate").asText()))
                .isEqualTo(java.time.Instant.parse("2026-01-05T00:00:00Z"));
    }

    /** Regression coverage: hourlyWageConfirmed's effect on a real order's price was never
     *  verified — only the confirm/unconfirm flag itself was tested (CostConfigChangeApiTest),
     *  never that effectiveHourlyWage() actually changes what a re-priced order quotes. */
    @Test
    void confirmingANewHourlyWageChangesARepricedOrdersLaborCost() throws Exception {
        String orderId = mvc.perform(auth(post("/api/ordertracker/groups/" + groupId + "/orders"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId":"%s","orderType":"INDIVIDUAL","createdByCreatorId":"%s",
                                 "itemName":"Wage test bear","orderReceivedDate":"2026-01-01T00:00:00Z",
                                 "mandatoryItems":[],"addOns":[],"craftingTimeHours":4}"""
                                .formatted(customerId, creatorAId)))
                .andExpect(status().isOk())
                // default unconfirmed wage is ₹100/h (BusinessConfig.DEFAULT_HOURLY_WAGE)
                .andExpect(jsonPath("$.costEstimate.laborCost").value(400.0))
                .andReturn().getResponse().getContentAsString();
        orderId = mapper.readTree(orderId).get("id").asText();

        String proposeBody = mvc.perform(auth(post(
                        "/api/ordertracker/groups/" + groupId + "/business-config/change-requests"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"overheadPercentage":0.15,"profitMarginPercentage":0.20,"hourlyWage":150}"""))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String requestId = mapper.readTree(proposeBody).get("id").asText();

        String tokenB = jwt.issue(users.findByEmailIgnoreCase("orderapitest-b@ot.test").orElseThrow());
        mvc.perform(auth(post("/api/ordertracker/groups/" + groupId
                        + "/business-config/change-requests/" + requestId + "/approve"), tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        mvc.perform(auth(get("/api/ordertracker/groups/" + groupId + "/business-config"), token))
                .andExpect(jsonPath("$.hourlyWageConfirmed").value(true))
                .andExpect(jsonPath("$.hourlyWage").value(150.0));

        // Re-pricing the same order (any edit recomputes costEstimate against the live
        // config) now uses the confirmed ₹150/h, not the old default.
        mvc.perform(auth(put("/api/ordertracker/groups/" + groupId + "/orders/" + orderId), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"itemName":"Wage test bear","orderReceivedDate":"2026-01-01T00:00:00Z",
                                 "mandatoryItems":[],"addOns":[],"craftingTimeHours":4}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.costEstimate.laborCost").value(600.0)); // 4h * ₹150
    }

    // ---- Bulk orders ----

    @Test
    void bulkOrderPricesVariantsAndDueDateIsDrivenBySlowestLoadedCreator() throws Exception {
        String body = mvc.perform(auth(post("/api/ordertracker/groups/" + groupId + "/orders"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId":"%s","orderType":"BULK","createdByCreatorId":"%s",
                                 "itemName":"Mini succulent crochet pots","orderReceivedDate":"2026-01-01T00:00:00Z",
                                 "coordinatingCreatorId":"%s",
                                 "variants":[
                                   {"label":"Blue flower","quantity":20,
                                    "mandatoryItems":[{"kind":"YARN","value":"Blue","quantity":1,"unitCost":100}],
                                    "craftingTimeHours":1,
                                    "splitAllocation":[{"creatorId":"%s","quantityAssigned":20}]},
                                   {"label":"Red flower","quantity":5,
                                    "mandatoryItems":[{"kind":"YARN","value":"Red","quantity":1,"unitCost":100}],
                                    "craftingTimeHours":1,
                                    "splitAllocation":[{"creatorId":"%s","quantityAssigned":5}]}
                                 ]}""".formatted(customerId, creatorAId, creatorAId, creatorAId, creatorBId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderType").value("BULK"))
                .andExpect(jsonPath("$.bulkDetails.totalQuantity").value(25))
                .andReturn().getResponse().getContentAsString();

        JsonNode view = mapper.readTree(body);
        // creator A (base, 4h/day): 20 units * 1h = 20h / 4h-per-day = 5 work days
        // creator B (8h/day): 5 units * 1h = 5h / 8h-per-day = 1 day -> driven by A
        // round 5 delivery redesign: 5 work days + the business's default same-city buffer(1)
        // = 6, padded by the default 15% time-overhead and rounded up = 7 days.
        String dueAt = view.get("bulkDetails").get("computedDueDate").asText();
        assertThat(java.time.Instant.parse(dueAt))
                .isEqualTo(java.time.Instant.parse("2026-01-01T00:00:00Z").plus(java.time.Duration.ofDays(7)));

        // round 5 pricing redesign: no overhead cost line; labor (1h crafting × the
        // business's default ₹100/h wage) folds into gross before the 20% profit margin.
        double perUnitCost = view.get("bulkDetails").get("variants").get(0).get("perUnitCost").asDouble();
        double materials = 100; // one mandatory item, no addons/packaging
        double labor = 1.0 * 100.0;
        double expectedGross = materials + labor;
        double expectedFinal = expectedGross * 1.20;
        assertThat(perUnitCost).isCloseTo(expectedFinal, org.assertj.core.data.Offset.offset(0.01));
    }

    /** Regression coverage for OrderBusinessRules.requireSplitAllocationSumsToQuantity —
     *  a named, user-facing validation rule with zero test coverage on either the create or
     *  the bulk-update path, despite completion-percentage and due-date math both silently
     *  going wrong if a variant's split ever drifts from its declared quantity. */
    @Test
    void rejectsCreatingABulkOrderWhoseSplitAllocationDoesNotSumToTheVariantQuantity() throws Exception {
        mvc.perform(auth(post("/api/ordertracker/groups/" + groupId + "/orders"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId":"%s","orderType":"BULK","createdByCreatorId":"%s",
                                 "itemName":"Mismatched split","orderReceivedDate":"2026-01-01T00:00:00Z",
                                 "variants":[{"label":"Blue flower","quantity":20,
                                   "mandatoryItems":[{"kind":"YARN","value":"Blue","quantity":1,"unitCost":100}],
                                   "craftingTimeHours":1,
                                   "splitAllocation":[{"creatorId":"%s","quantityAssigned":15}]}]}"""
                                .formatted(customerId, creatorAId, creatorAId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("quantity of 20"),
                        org.hamcrest.Matchers.containsString("adds up to 15"))));
    }

    @Test
    void rejectsUpdatingBulkDetailsWhoseSplitAllocationDoesNotSumToTheVariantQuantity() throws Exception {
        String body = mvc.perform(auth(post("/api/ordertracker/groups/" + groupId + "/orders"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId":"%s","orderType":"BULK","createdByCreatorId":"%s",
                                 "itemName":"Valid at first","orderReceivedDate":"2026-01-01T00:00:00Z",
                                 "variants":[{"label":"Blue flower","quantity":20,
                                   "mandatoryItems":[{"kind":"YARN","value":"Blue","quantity":1,"unitCost":100}],
                                   "craftingTimeHours":1,
                                   "splitAllocation":[{"creatorId":"%s","quantityAssigned":20}]}]}"""
                                .formatted(customerId, creatorAId, creatorAId)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String orderId = mapper.readTree(body).get("id").asText();

        mvc.perform(auth(put("/api/ordertracker/groups/" + groupId + "/orders/" + orderId + "/bulk-details"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId":"%s","itemName":"Now mismatched","orderReceivedDate":"2026-01-01T00:00:00Z",
                                 "researchTimeHours":0,"variants":[{"label":"Blue flower","quantity":20,
                                   "mandatoryItems":[{"kind":"YARN","value":"Blue","quantity":1,"unitCost":100}],
                                   "craftingTimeHours":1,
                                   "splitAllocation":[{"creatorId":"%s","quantityAssigned":8}]}],
                                 "logisticsBufferDays":0}"""
                                .formatted(customerId, creatorAId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("quantity of 20"),
                        org.hamcrest.Matchers.containsString("adds up to 8"))));
    }

    @Test
    void bulkVariantAssemblyTimeAndOrderLevelResearchTimeBothPadTheDueDate() throws Exception {
        // creator A is 4h/day (setUp) and is both the sole split assignee and (by default,
        // since coordinatingCreatorId is omitted) the coordinating creator research time is
        // charged against.
        String body = mvc.perform(auth(post("/api/ordertracker/groups/" + groupId + "/orders"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId":"%s","orderType":"BULK","createdByCreatorId":"%s",
                                 "itemName":"Mini succulent crochet pots","orderReceivedDate":"2026-01-01T00:00:00Z",
                                 "researchTimeHours":8,
                                 "variants":[{"label":"Blue flower","quantity":20,
                                   "mandatoryItems":[{"kind":"YARN","value":"Blue","quantity":1,"unitCost":100}],
                                   "craftingTimeHours":1,"assemblyTimeHours":1,
                                   "splitAllocation":[{"creatorId":"%s","quantityAssigned":20}]}]}"""
                                .formatted(customerId, creatorAId, creatorAId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bulkDetails.variants[0].craftingTimeHours").value(1.0))
                .andExpect(jsonPath("$.bulkDetails.variants[0].assemblyTimeHours").value(1.0))
                // perUnitTimeHours = 1 (crochet) + 1 (assembly) + 0 (packaging) = 2h/unit
                .andExpect(jsonPath("$.bulkDetails.variants[0].perUnitTimeHours").value(2.0))
                .andReturn().getResponse().getContentAsString();

        // creator A: 20 units * 2h/unit = 40h / 4h-per-day = 10 days for the work itself,
        // plus 8h research / 4h-per-day = 2 days charged against the coordinating creator's
        // own pace (defaults to the same creator here), plus the business's default
        // same-city delivery buffer (1) = 13, padded by the default 15% time-overhead and
        // rounded up (round 5 redesign) = 15 days total.
        JsonNode view = mapper.readTree(body);
        assertThat(java.time.Instant.parse(view.get("bulkDetails").get("computedDueDate").asText()))
                .isEqualTo(java.time.Instant.parse("2026-01-01T00:00:00Z").plus(java.time.Duration.ofDays(15)));
    }
}
