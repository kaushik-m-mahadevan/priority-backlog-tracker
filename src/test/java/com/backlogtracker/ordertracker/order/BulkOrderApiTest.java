package com.backlogtracker.ordertracker.order;

import static org.assertj.core.api.Assertions.assertThat;
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
import com.backlogtracker.ordertracker.order.repository.BulkOrderRepository;
import com.backlogtracker.support.AuthTestSupport;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class BulkOrderApiTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired UserRepository users;
    @Autowired GroupRepository groups;
    @Autowired JwtService jwt;
    @Autowired BulkOrderRepository bulkOrders;
    @Autowired CustomerRepository customers;
    @Autowired CreatorRepository creators;
    @Autowired BusinessConfigRepository businessConfigs;

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
        bulkOrders.deleteAll();
        customers.deleteAll();
        creators.deleteAll();
        businessConfigs.deleteAll();
        for (String e : new String[] {"bulkmember2@ot.test"}) {
            users.findByEmailIgnoreCase(e).ifPresent(users::delete);
        }

        token = AuthTestSupport.devToken(mvc, mapper);
        groupId = AuthTestSupport.createGroup(mvc, mapper, token, "Crochet Co " + System.nanoTime());

        String creatorABody = mvc.perform(auth(put("/api/ordertracker/groups/" + groupId + "/creators/me"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"baseLocation":"Bangalore","hoursAvailablePerDay":4}"""))
                .andReturn().getResponse().getContentAsString();
        creatorAId = mapper.readTree(creatorABody).get("id").asText();

        User member2 = users.save(User.builder().name("Member Two").email("bulkmember2@ot.test")
                .passwordHash("x").role(Role.USER).status(AccountStatus.ACTIVE).handle("bulkmember2").build());
        groups.findById(groupId).ifPresent(g -> {
            g.getMemberIds().add(member2.getId());
            groups.save(g);
        });
        String member2Token = jwt.issue(member2);
        String creatorBBody = mvc.perform(auth(put("/api/ordertracker/groups/" + groupId + "/creators/me"), member2Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"baseLocation":"Chennai","hoursAvailablePerDay":8}"""))
                .andReturn().getResponse().getContentAsString();
        creatorBId = mapper.readTree(creatorBBody).get("id").asText();

        String customerBody = mvc.perform(auth(post("/api/ordertracker/groups/" + groupId + "/customers"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Big Retail Buyer","acquisitionChannel":"REFERRAL"}"""))
                .andReturn().getResponse().getContentAsString();
        customerId = mapper.readTree(customerBody).get("id").asText();
    }

    @Test
    void bulkOrderDueDateIsDrivenByTheSlowestLoadedCreatorSplit() throws Exception {
        String body = mvc.perform(auth(post("/api/ordertracker/groups/" + groupId + "/bulk-orders"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId":"%s","description":"Corporate gift batch",
                                 "variants":[{"label":"Small","quantity":20,"mandatoryItems":{"wool":"Cotton"}},
                                             {"label":"Large","quantity":5,"mandatoryItems":{"wool":"Cotton"}}],
                                 "creatorSplits":[{"creatorId":"%s","assignedQuantity":20,"estimatedHoursPerUnit":1},
                                                  {"creatorId":"%s","assignedQuantity":5,"estimatedHoursPerUnit":1}],
                                 "materialsCost":2000}""".formatted(customerId, creatorAId, creatorBId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderNumber").value(org.hamcrest.Matchers.hasLength(14)))
                .andExpect(jsonPath("$.totalQuantity").value(25))
                .andExpect(jsonPath("$.overallCompletionPercent").value(0.0))
                .andReturn().getResponse().getContentAsString();
        String orderId = mapper.readTree(body).get("id").asText();
        String createdAt = mapper.readTree(body).get("createdAt").asText();
        String dueAt = mapper.readTree(body).get("computedDueDate").asText();

        // creator A: 20h/4h-per-day = 5 days; creator B: 5h/8h-per-day = 1 day -> due date driven by A
        assertThat(java.time.Duration.between(java.time.Instant.parse(createdAt), java.time.Instant.parse(dueAt)))
                .isEqualTo(java.time.Duration.ofDays(5));

        mvc.perform(auth(patch("/api/ordertracker/groups/" + groupId + "/bulk-orders/" + orderId
                        + "/creator-splits/" + creatorAId), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"completionFraction":1.0}"""))
                .andExpect(status().isOk())
                // creator A holds 20/25 = 80% of the batch, now fully done -> 80% overall
                .andExpect(jsonPath("$.overallCompletionPercent").value(80.0));
    }

    @Test
    void bulkOrderRequiresAtLeastOneCreatorSplit() throws Exception {
        mvc.perform(auth(post("/api/ordertracker/groups/" + groupId + "/bulk-orders"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId":"%s","variants":[{"label":"Small","quantity":1}],
                                 "creatorSplits":[],"materialsCost":0}""".formatted(customerId)))
                .andExpect(status().isBadRequest());
    }
}
