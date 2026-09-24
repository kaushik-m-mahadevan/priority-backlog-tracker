package com.backlogtracker.ordertracker.order;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.backlogtracker.commons.group.repository.GroupRepository;
import com.backlogtracker.commons.security.JwtService;
import com.backlogtracker.commons.user.repository.UserRepository;
import com.backlogtracker.ordertracker.customer.repository.CustomerRepository;
import com.backlogtracker.ordertracker.master.repository.BusinessConfigRepository;
import com.backlogtracker.ordertracker.master.repository.CreatorRepository;
import com.backlogtracker.ordertracker.order.repository.OrderRepository;
import com.backlogtracker.support.AuthTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class OrderTimeLogApiTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired UserRepository users;
    @Autowired GroupRepository groups;
    @Autowired JwtService jwt;
    @Autowired OrderRepository orders;
    @Autowired CustomerRepository customers;
    @Autowired CreatorRepository creators;
    @Autowired BusinessConfigRepository businessConfigs;

    private String token;
    private String groupId;
    private String customerId;
    private String creatorAId;

    private MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder b, String t) {
        return b.header("Authorization", "Bearer " + t);
    }

    @BeforeEach
    void setUp() throws Exception {
        orders.deleteAll();
        customers.deleteAll();
        creators.deleteAll();
        businessConfigs.deleteAll();
        groups.deleteAll();

        token = AuthTestSupport.devToken(mvc, mapper);
        groupId = AuthTestSupport.createGroup(mvc, mapper, token, "Time Log Test " + System.nanoTime());

        String creatorABody = mvc.perform(auth(put("/api/ordertracker/groups/" + groupId + "/creators/me"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"baseLocation":"Bangalore","hoursAvailablePerDay":4}"""))
                .andReturn().getResponse().getContentAsString();
        creatorAId = mapper.readTree(creatorABody).get("id").asText();

        String customerBody = mvc.perform(auth(post("/api/ordertracker/groups/" + groupId + "/customers"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Priya Sharma","acquisitionChannel":"INSTAGRAM"}"""))
                .andReturn().getResponse().getContentAsString();
        customerId = mapper.readTree(customerBody).get("id").asText();
    }

    private String createIndividualOrder() throws Exception {
        String body = mvc.perform(auth(post("/api/ordertracker/groups/" + groupId + "/orders"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId":"%s","orderType":"INDIVIDUAL","createdByCreatorId":"%s",
                                 "itemName":"Amigurumi bear","orderReceivedDate":"2026-01-01T00:00:00Z",
                                 "mandatoryItems":[{"kind":"YARN","value":"Cream","quantity":1,"unitCost":100}],
                                 "researchTimeHours":1,"craftingTimeHours":4,"assemblyTimeHours":1}"""
                                .formatted(customerId, creatorAId)))
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(body).get("id").asText();
    }

    private String createBulkOrder() throws Exception {
        String body = mvc.perform(auth(post("/api/ordertracker/groups/" + groupId + "/orders"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId":"%s","orderType":"BULK","createdByCreatorId":"%s",
                                 "itemName":"Keychains","orderReceivedDate":"2026-01-01T00:00:00Z",
                                 "variants":[{"label":"Blue","quantity":10,
                                   "mandatoryItems":[{"kind":"YARN","value":"Blue","quantity":1,"unitCost":50}],
                                   "craftingTimeHours":2,"assemblyTimeHours":0.5,
                                   "splitAllocation":[{"creatorId":"%s","quantityAssigned":10}]}]}"""
                                .formatted(customerId, creatorAId, creatorAId)))
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(body).get("id").asText();
    }

    @Test
    void logsResearchTimeAgainstAnIndividualOrder() throws Exception {
        String orderId = createIndividualOrder();
        String body = mvc.perform(auth(post(timeLogUrl(orderId)), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"stage":"RESEARCH","hours":0.5,"note":"looked up ear placement"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timeLogEntries.length()").value(1))
                .andExpect(jsonPath("$.timeLogEntries[0].stage").value("RESEARCH"))
                .andExpect(jsonPath("$.timeLogEntries[0].hours").value(0.5))
                .andExpect(jsonPath("$.timeLogEntries[0].note").value("looked up ear placement"))
                .andReturn().getResponse().getContentAsString();
        JsonNode entry = mapper.readTree(body).get("timeLogEntries").get(0);
        org.assertj.core.api.Assertions.assertThat(entry.get("loggedByCreatorId").asText()).isEqualTo(creatorAId);
    }

    @Test
    void logsCraftingTimeAgainstAnIndividualOrderAtTheOrderLevel() throws Exception {
        String orderId = createIndividualOrder();
        mvc.perform(auth(post(timeLogUrl(orderId)), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"stage":"CRAFTING","hours":1.25}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timeLogEntries.length()").value(1))
                .andExpect(jsonPath("$.timeLogEntries[0].stage").value("CRAFTING"));
    }

    @Test
    void rejectsAVariantIdOnAnIndividualOrder() throws Exception {
        String orderId = createIndividualOrder();
        mvc.perform(auth(post(timeLogUrl(orderId)), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"stage":"CRAFTING","hours":1,"variantId":"whatever"}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void requiresAVariantIdForCraftingTimeOnABulkOrder() throws Exception {
        String orderId = createBulkOrder();
        mvc.perform(auth(post(timeLogUrl(orderId)), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"stage":"CRAFTING","hours":1}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void logsCraftingTimeAgainstTheNamedVariantOnABulkOrder() throws Exception {
        String orderId = createBulkOrder();
        String variantId = getFirstVariantId(orderId);

        String body = mvc.perform(auth(post(timeLogUrl(orderId)), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"stage":"CRAFTING","hours":1.5,"variantId":"%s"}""".formatted(variantId)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode variant = mapper.readTree(body).get("bulkDetails").get("variants").get(0);
        org.assertj.core.api.Assertions.assertThat(variant.get("timeLogEntries").size()).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(variant.get("timeLogEntries").get(0).get("hours").asDouble()).isEqualTo(1.5);
        // order-level list stays untouched — this entry belongs to the variant only
        org.assertj.core.api.Assertions.assertThat(mapper.readTree(body).get("timeLogEntries").size()).isEqualTo(0);
    }

    @Test
    void rejectsResearchTimeWithAVariantIdEvenOnABulkOrder() throws Exception {
        String orderId = createBulkOrder();
        String variantId = getFirstVariantId(orderId);
        mvc.perform(auth(post(timeLogUrl(orderId)), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"stage":"RESEARCH","hours":1,"variantId":"%s"}""".formatted(variantId)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsHoursThatArentOnAWholeMinuteStep() throws Exception {
        String orderId = createIndividualOrder();
        mvc.perform(auth(post(timeLogUrl(orderId)), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"stage":"CRAFTING","hours":0.31}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void acceptsHoursOnAWholeMinuteStepThatIsntAQuarterHour() throws Exception {
        String orderId = createIndividualOrder();
        mvc.perform(auth(post(timeLogUrl(orderId)), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"stage":"CRAFTING","hours":0.3}"""))
                .andExpect(status().isOk());
    }

    @Test
    void removingAnEntryTakesItOutOfTheList() throws Exception {
        String orderId = createIndividualOrder();
        String body = mvc.perform(auth(post(timeLogUrl(orderId)), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"stage":"CRAFTING","hours":1}"""))
                .andReturn().getResponse().getContentAsString();
        String entryId = mapper.readTree(body).get("timeLogEntries").get(0).get("entryId").asText();

        mvc.perform(auth(delete(timeLogUrl(orderId) + "/" + entryId), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timeLogEntries.length()").value(0));
    }

    /** Regression coverage for a gap the round 4 review flagged: this codebase documents
     *  (in OrderService.toComponents's own comment) that omitting a component from a
     *  resubmitted order drops it — and its logged time — entirely, matching the same
     *  full-replace semantics already used for variants/envelope fields. That was a design
     *  decision, not a bug, but nothing actually exercised it. Verifies the documented
     *  behavior really happens: a component with real logged time against it, once left out
     *  of an edit, is gone from the response with no error and no orphaned data. */
    @Test
    void removingAComponentInAnEditDropsItAndItsLoggedTimeWithoutError() throws Exception {
        String templateBody = mvc.perform(auth(post("/api/ordertracker/groups/" + groupId + "/component-templates"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"label":"Vase base","baseCraftingTimeHours":2.5}"""))
                .andReturn().getResponse().getContentAsString();
        String templateId = mapper.readTree(templateBody).get("id").asText();

        String orderBody = mvc.perform(auth(post("/api/ordertracker/groups/" + groupId + "/orders"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId":"%s","orderType":"INDIVIDUAL","createdByCreatorId":"%s",
                                 "itemName":"Spring bouquet vase","orderReceivedDate":"2026-01-01T00:00:00Z",
                                 "components":[{"componentId":"comp-1","templateId":"%s","quantity":1,
                                   "mandatoryItems":[],"addOns":[],"craftingTimeHours":2.5}]}"""
                                .formatted(customerId, creatorAId, templateId)))
                .andReturn().getResponse().getContentAsString();
        String orderId = mapper.readTree(orderBody).get("id").asText();

        mvc.perform(auth(post(timeLogUrl(orderId)), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"stage":"CRAFTING","hours":1.5,"componentId":"comp-1"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components[0].timeLogEntries.length()").value(1));

        // Re-submit the order with no components at all — the documented full-replace drop.
        mvc.perform(auth(put("/api/ordertracker/groups/" + groupId + "/orders/" + orderId), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"itemName":"Spring bouquet vase","orderReceivedDate":"2026-01-01T00:00:00Z",
                                 "mandatoryItems":[],"addOns":[],"components":[],"craftingTimeHours":0}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.length()").value(0));

        // Logging time against the now-gone componentId is a clean 404, not a server error
        // or a silent no-op that would leave a phantom entry somewhere.
        mvc.perform(auth(post(timeLogUrl(orderId)), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"stage":"CRAFTING","hours":1,"componentId":"comp-1"}"""))
                .andExpect(status().isNotFound());
    }

    private String timeLogUrl(String orderId) {
        return "/api/ordertracker/groups/" + groupId + "/orders/" + orderId + "/time-log";
    }

    private String getFirstVariantId(String orderId) throws Exception {
        String body = mvc.perform(auth(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                                "/api/ordertracker/groups/" + groupId + "/orders/" + orderId), token))
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(body).get("bulkDetails").get("variants").get(0).get("variantId").asText();
    }
}
