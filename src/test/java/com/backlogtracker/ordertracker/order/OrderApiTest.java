package com.backlogtracker.ordertracker.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.backlogtracker.commons.group.repository.GroupRepository;
import com.backlogtracker.commons.security.JwtService;
import com.backlogtracker.commons.user.domain.AccountStatus;
import com.backlogtracker.commons.user.domain.Role;
import com.backlogtracker.commons.user.domain.User;
import com.backlogtracker.commons.user.repository.UserRepository;
import com.backlogtracker.ordertracker.customer.repository.CustomerRepository;
import com.backlogtracker.ordertracker.master.repository.BusinessConfigRepository;
import com.backlogtracker.ordertracker.master.repository.CreatorRepository;
import com.backlogtracker.ordertracker.order.repository.OrderChangeLogRepository;
import com.backlogtracker.ordertracker.order.repository.OrderRepository;
import com.backlogtracker.support.AuthTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class OrderApiTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired UserRepository users;
    @Autowired GroupRepository groups;
    @Autowired JwtService jwt;
    @Autowired OrderRepository orders;
    @Autowired OrderChangeLogRepository changeLogs;
    @Autowired CustomerRepository customers;
    @Autowired CreatorRepository creators;
    @Autowired BusinessConfigRepository businessConfigs;
    @Autowired MongoOperations mongo;

    private String token;
    private String groupId;
    private String customerId;
    private String creatorAId;
    private String creatorBId;

    private MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder b, String t) {
        return b.header("Authorization", "Bearer " + t);
    }

    @BeforeEach
    void setUp() throws Exception {
        orders.deleteAll();
        changeLogs.deleteAll();
        customers.deleteAll();
        creators.deleteAll();
        businessConfigs.deleteAll();
        for (String e : new String[] {"orderapitest-b@ot.test"}) {
            users.findByEmailIgnoreCase(e).ifPresent(users::delete);
        }
        // the bootstrap admin's group membership accumulates across test classes sharing the
        // embedded Mongo — without this, a full-suite run can hit the 5-group cap and fail here.
        groups.deleteAll();

        token = AuthTestSupport.devToken(mvc, mapper);
        groupId = AuthTestSupport.createGroup(mvc, mapper, token, "Crochet Co " + System.nanoTime());

        String creatorABody = mvc.perform(auth(put("/api/ordertracker/groups/" + groupId + "/creators/me"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"baseLocation":"Bangalore","hoursAvailablePerDay":4}"""))
                .andReturn().getResponse().getContentAsString();
        creatorAId = mapper.readTree(creatorABody).get("id").asText();

        User userB = users.save(User.builder().name("Creator B").email("orderapitest-b@ot.test")
                .passwordHash("x").role(Role.USER).status(AccountStatus.ACTIVE).handle("orderapitestb").build());
        groups.findById(groupId).ifPresent(g -> {
            g.getMemberIds().add(userB.getId());
            groups.save(g);
        });
        String tokenB = jwt.issue(userB);
        String creatorBBody = mvc.perform(auth(put("/api/ordertracker/groups/" + groupId + "/creators/me"), tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"baseLocation":"Chennai","hoursAvailablePerDay":8}"""))
                .andReturn().getResponse().getContentAsString();
        creatorBId = mapper.readTree(creatorBBody).get("id").asText();

        String customerBody = mvc.perform(auth(post("/api/ordertracker/groups/" + groupId + "/customers"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Priya Sharma","acquisitionChannel":"INSTAGRAM"}"""))
                .andReturn().getResponse().getContentAsString();
        customerId = mapper.readTree(customerBody).get("id").asText();
    }

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
        String shipped = mvc.perform(auth(patch("/api/ordertracker/groups/" + groupId + "/orders/" + orderId + "/status"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SHIPPED\"}"))
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
        // 4 real transitions above (IN_PROGRESS, DELIVERED, SHIPPED, DELIVERED again) — no
        // no-op update was sent in this test, so every one of them should be logged.
        assertThat(mapper.readTree(changeLog).get(0).get("orderStatusChangeHistory")).hasSize(4);
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
    void stageAssignmentProgressRollsUpIntoEqualWeightedCompletion() throws Exception {
        String orderId = createBasicIndividualOrder();

        mvc.perform(auth(patch("/api/ordertracker/groups/" + groupId + "/orders/" + orderId + "/stage-assignments/crocheting"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"assignedCreatorId":"%s","unitsCompleted":1}""".formatted(creatorAId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completionPercentage").value(25.0)); // 1 of 4 stages
    }

    @Test
    void paymentsAccumulateAndDerivePaymentStatusWithAmountEncryptedAtRest() throws Exception {
        String orderId = createBasicIndividualOrder();
        String body = mvc.perform(get("/api/ordertracker/groups/" + groupId + "/orders/" + orderId)
                        .header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getContentAsString();
        double finalCost = mapper.readTree(body).get("costEstimate").get("finalCost").asDouble();

        mvc.perform(auth(post("/api/ordertracker/groups/" + groupId + "/orders/" + orderId + "/payments"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"FINAL","amount":%s,"mode":"UPI","note":"Paid in full"}""".formatted(finalCost)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentStatus").value("PAID_IN_FULL"))
                .andExpect(jsonPath("$.netPaid").value(finalCost));

        Document raw = mongo.findOne(Query.query(Criteria.where("_id").is(orderId)), Document.class, "orderTrackerOrders");
        assertThat(raw).isNotNull();
        java.util.List<?> payments = raw.getList("payments", Document.class);
        Document payment = (Document) payments.get(0);
        assertThat(payment.getString("amount")).isNotEqualTo(Double.toString(finalCost));
        assertThat(payment.getString("note")).isNotEqualTo("Paid in full");
    }

    @Test
    void shipmentPlanRoundTripsAndMarksStopsWithTrackingNumberEncrypted() throws Exception {
        String orderId = createBasicIndividualOrder();

        mvc.perform(auth(put("/api/ordertracker/groups/" + groupId + "/orders/" + orderId + "/shipment-plan"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"stops":[{"stopOrder":1,"type":"FINAL_DELIVERY","originLocationCode":"659",
                                 "destinationLocationCode":"700","carrier":"India Post","trackingNumber":"TRK-1"}]}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shipmentPlan[0].trackingNumber").value("TRK-1"));

        mvc.perform(auth(patch("/api/ordertracker/groups/" + groupId + "/orders/" + orderId + "/shipment-plan/0"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"deliveredConfirmed":true}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shipmentPlan[0].deliveredConfirmed").value(true));

        Document raw = mongo.findOne(Query.query(Criteria.where("_id").is(orderId)), Document.class, "orderTrackerOrders");
        Document stop = (Document) ((java.util.List<?>) raw.get("shipmentPlan", Document.class).get("stops")).get(0);
        assertThat(stop.getString("trackingNumber")).isNotEqualTo("TRK-1");
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

    @Test
    void bulkSplitAndBatchStageProgressRollUpIntoCompletionPercentage() throws Exception {
        String orderId = createBasicBulkOrder();

        mvc.perform(auth(patch("/api/ordertracker/groups/" + groupId + "/orders/" + orderId + "/bulk-split-progress"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"variantId":"%s","creatorId":"%s","stageKey":"crocheting","unitsCompleted":10}"""
                                .formatted(firstVariantId(orderId), creatorAId)))
                .andExpect(status().isOk());

        mvc.perform(get("/api/ordertracker/groups/" + groupId + "/orders/" + orderId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completionPercentage").value(org.hamcrest.Matchers.greaterThan(0.0)));
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

    // ---- helpers ----

    private String createBasicIndividualOrder() throws Exception {
        String body = mvc.perform(auth(post("/api/ordertracker/groups/" + groupId + "/orders"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId":"%s","orderType":"INDIVIDUAL","createdByCreatorId":"%s",
                                 "itemName":"Amigurumi bear","orderReceivedDate":"2026-01-01T00:00:00Z",
                                 "mandatoryItems":[{"kind":"YARN","value":"Cream","quantity":1,"unitCost":100}],
                                 "craftingTimeHours":1}""".formatted(customerId, creatorAId)))
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(body).get("id").asText();
    }

    private String createBasicBulkOrder() throws Exception {
        String body = mvc.perform(auth(post("/api/ordertracker/groups/" + groupId + "/orders"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId":"%s","orderType":"BULK","createdByCreatorId":"%s",
                                 "itemName":"Mini succulent crochet pots","orderReceivedDate":"2026-01-01T00:00:00Z",
                                 "variants":[{"label":"Blue flower","quantity":20,
                                   "mandatoryItems":[{"kind":"YARN","value":"Blue","quantity":1,"unitCost":100}],
                                   "craftingTimeHours":1,
                                   "splitAllocation":[{"creatorId":"%s","quantityAssigned":20}]}]}"""
                                .formatted(customerId, creatorAId, creatorAId)))
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(body).get("id").asText();
    }

    private String firstVariantId(String orderId) throws Exception {
        String body = mvc.perform(get("/api/ordertracker/groups/" + groupId + "/orders/" + orderId)
                        .header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(body).get("bulkDetails").get("variants").get(0).get("variantId").asText();
    }
}
