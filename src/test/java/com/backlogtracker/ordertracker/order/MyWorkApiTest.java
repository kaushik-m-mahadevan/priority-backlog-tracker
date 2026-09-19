package com.backlogtracker.ordertracker.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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

/** Covers OrderService.myWork(): which orders show up for a caller's own Creator profile,
 *  the pending/done/all completion filter, the orderReceivedDate range filter, and the
 *  no-creator-profile-yet case. */
@SpringBootTest
@AutoConfigureMockMvc
class MyWorkApiTest {

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

    private String tokenA;
    private String tokenB;
    private String groupId;
    private String customerId;
    private String creatorAId;

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
        for (String e : new String[] {"myworktest-b@ot.test", "myworktest-c@ot.test"}) {
            users.findByEmailIgnoreCase(e).ifPresent(users::delete);
        }
        // the bootstrap admin's group membership accumulates across test classes sharing the
        // embedded Mongo — without this, a full-suite run can hit the 5-group cap and fail here.
        groups.deleteAll();

        tokenA = AuthTestSupport.devToken(mvc, mapper);
        groupId = AuthTestSupport.createGroup(mvc, mapper, tokenA, "MyWork Test " + System.nanoTime());

        String creatorABody = mvc.perform(auth(put("/api/ordertracker/groups/" + groupId + "/creators/me"), tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"baseLocation":"Bangalore","hoursAvailablePerDay":4}"""))
                .andReturn().getResponse().getContentAsString();
        creatorAId = mapper.readTree(creatorABody).get("id").asText();

        User userB = users.save(User.builder().name("Creator B").email("myworktest-b@ot.test")
                .passwordHash("x").role(Role.USER).status(AccountStatus.ACTIVE).handle("myworktestb").build());
        groups.findById(groupId).ifPresent(g -> {
            g.getMemberIds().add(userB.getId());
            groups.save(g);
        });
        tokenB = jwt.issue(userB);
        mvc.perform(auth(put("/api/ordertracker/groups/" + groupId + "/creators/me"), tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"baseLocation":"Chennai","hoursAvailablePerDay":8}"""))
                .andExpect(status().isOk());

        String customerBody = mvc.perform(auth(post("/api/ordertracker/groups/" + groupId + "/customers"), tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Priya Sharma","acquisitionChannel":"INSTAGRAM"}"""))
                .andReturn().getResponse().getContentAsString();
        customerId = mapper.readTree(customerBody).get("id").asText();
    }

    @Test
    void anOrderCreatedByMeIsAssignedToMeAndShowsUpUnderTheDefaultPendingFilter() throws Exception {
        createIndividualOrder(tokenA, creatorAId, "2026-01-01T00:00:00Z");

        mvc.perform(get("/api/ordertracker/groups/" + groupId + "/orders/my-work")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].itemName").value("Amigurumi bear"));
    }

    @Test
    void anOrderCreatedByAnotherCreatorDoesNotShowUpInMyWork() throws Exception {
        // creator B logs their own order — every stage auto-assigns to B, not A
        String creatorBBody = mvc.perform(get("/api/ordertracker/groups/" + groupId + "/creators")
                        .header("Authorization", "Bearer " + tokenA))
                .andReturn().getResponse().getContentAsString();
        String creatorBId = null;
        for (JsonNode c : mapper.readTree(creatorBBody)) {
            if (!c.get("id").asText().equals(creatorAId)) {
                creatorBId = c.get("id").asText();
            }
        }
        createIndividualOrder(tokenB, creatorBId, "2026-01-01T00:00:00Z");

        mvc.perform(get("/api/ordertracker/groups/" + groupId + "/orders/my-work")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void completingEveryStageForMeMovesTheOrderFromPendingToDone() throws Exception {
        String orderId = createIndividualOrder(tokenA, creatorAId, "2026-01-01T00:00:00Z");

        mvc.perform(get("/api/ordertracker/groups/" + groupId + "/orders/my-work?completionFilter=pending")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/ordertracker/groups/" + groupId + "/orders/my-work?completionFilter=done")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(jsonPath("$.length()").value(0));

        for (String stageKey : new String[] {"crocheting", "assembly", "packaging", "shipment"}) {
            mvc.perform(auth(patch("/api/ordertracker/groups/" + groupId + "/orders/" + orderId
                                    + "/stage-assignments/" + stageKey), tokenA)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"assignedCreatorId":"%s","unitsCompleted":1}""".formatted(creatorAId)))
                    .andExpect(status().isOk());
        }

        mvc.perform(get("/api/ordertracker/groups/" + groupId + "/orders/my-work?completionFilter=pending")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/ordertracker/groups/" + groupId + "/orders/my-work?completionFilter=done")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void allFilterIncludesBothPendingAndDoneOrdersForMe() throws Exception {
        String pendingId = createIndividualOrder(tokenA, creatorAId, "2026-01-01T00:00:00Z");
        String doneId = createIndividualOrder(tokenA, creatorAId, "2026-01-02T00:00:00Z");
        for (String stageKey : new String[] {"crocheting", "assembly", "packaging", "shipment"}) {
            mvc.perform(auth(patch("/api/ordertracker/groups/" + groupId + "/orders/" + doneId
                                    + "/stage-assignments/" + stageKey), tokenA)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"assignedCreatorId":"%s","unitsCompleted":1}""".formatted(creatorAId)))
                    .andExpect(status().isOk());
        }

        String body = mvc.perform(get("/api/ordertracker/groups/" + groupId + "/orders/my-work?completionFilter=all")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andReturn().getResponse().getContentAsString();

        java.util.Set<String> ids = new java.util.HashSet<>();
        for (JsonNode o : mapper.readTree(body)) {
            ids.add(o.get("id").asText());
        }
        assertThat(ids).containsExactlyInAnyOrder(pendingId, doneId);
    }

    @Test
    void dateRangeFilterOnlyIncludesOrdersReceivedWithinRange() throws Exception {
        createIndividualOrder(tokenA, creatorAId, "2026-01-01T00:00:00Z");

        mvc.perform(get("/api/ordertracker/groups/" + groupId
                        + "/orders/my-work?from=2026-02-01T00:00:00Z&to=2026-03-01T00:00:00Z")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        mvc.perform(get("/api/ordertracker/groups/" + groupId
                        + "/orders/my-work?from=2025-12-01T00:00:00Z&to=2026-01-31T00:00:00Z")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void bulkOrderSplitAllocationDrivesMyWorkAndSplitTrackedProgressDrivesDone() throws Exception {
        String body = mvc.perform(auth(post("/api/ordertracker/groups/" + groupId + "/orders"), tokenA)
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
        JsonNode created = mapper.readTree(body);
        String orderId = created.get("id").asText();
        String variantId = created.get("bulkDetails").get("variants").get(0).get("variantId").asText();

        mvc.perform(get("/api/ordertracker/groups/" + groupId + "/orders/my-work?completionFilter=pending")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(jsonPath("$.length()").value(1));

        // only crocheting/assembly are split-tracked by default — packaging/shipment are
        // batch-tracked and don't factor into isCompleteForMe for a bulk order.
        for (String stageKey : new String[] {"crocheting", "assembly"}) {
            mvc.perform(auth(patch("/api/ordertracker/groups/" + groupId + "/orders/" + orderId + "/bulk-split-progress"), tokenA)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"variantId":"%s","creatorId":"%s","stageKey":"%s","unitsCompleted":20}"""
                                    .formatted(variantId, creatorAId, stageKey)))
                    .andExpect(status().isOk());
        }

        mvc.perform(get("/api/ordertracker/groups/" + groupId + "/orders/my-work?completionFilter=pending")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/ordertracker/groups/" + groupId + "/orders/my-work?completionFilter=done")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void callerWithNoCreatorProfileYetGetsAnEmptyListNotAnError() throws Exception {
        createIndividualOrder(tokenA, creatorAId, "2026-01-01T00:00:00Z");

        User userC = users.save(User.builder().name("No Profile").email("myworktest-c@ot.test")
                .passwordHash("x").role(Role.USER).status(AccountStatus.ACTIVE).handle("myworktestc").build());
        groups.findById(groupId).ifPresent(g -> {
            g.getMemberIds().add(userC.getId());
            groups.save(g);
        });
        String tokenC = jwt.issue(userC);

        mvc.perform(get("/api/ordertracker/groups/" + groupId + "/orders/my-work")
                        .header("Authorization", "Bearer " + tokenC))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    private String createIndividualOrder(String token, String createdByCreatorId, String orderReceivedDate) throws Exception {
        String body = mvc.perform(auth(post("/api/ordertracker/groups/" + groupId + "/orders"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId":"%s","orderType":"INDIVIDUAL","createdByCreatorId":"%s",
                                 "itemName":"Amigurumi bear","orderReceivedDate":"%s",
                                 "mandatoryItems":[{"kind":"YARN","value":"Cream","quantity":1,"unitCost":100}],
                                 "craftingTimeHours":1}""".formatted(customerId, createdByCreatorId, orderReceivedDate)))
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(body).get("id").asText();
    }
}
