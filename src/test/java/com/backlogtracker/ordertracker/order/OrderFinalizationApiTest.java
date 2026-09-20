package com.backlogtracker.ordertracker.order;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import com.backlogtracker.commons.notification.repository.NotificationRepository;
import com.backlogtracker.commons.security.JwtService;
import com.backlogtracker.commons.user.domain.AccountStatus;
import com.backlogtracker.commons.user.domain.Role;
import com.backlogtracker.commons.user.domain.User;
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
class OrderFinalizationApiTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired UserRepository users;
    @Autowired GroupRepository groups;
    @Autowired JwtService jwt;
    @Autowired OrderRepository orders;
    @Autowired CustomerRepository customers;
    @Autowired CreatorRepository creators;
    @Autowired BusinessConfigRepository businessConfigs;
    @Autowired NotificationRepository notifications;

    private String token;
    private String tokenB;
    private String groupId;
    private String orderId;

    private MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder b, String t) {
        return b.header("Authorization", "Bearer " + t);
    }

    @BeforeEach
    void setUp() throws Exception {
        orders.deleteAll();
        customers.deleteAll();
        creators.deleteAll();
        businessConfigs.deleteAll();
        for (String e : new String[] {"finalizationtest-b@ot.test"}) {
            users.findByEmailIgnoreCase(e).ifPresent(users::delete);
        }
        groups.deleteAll();

        token = AuthTestSupport.devToken(mvc, mapper);
        groupId = AuthTestSupport.createGroup(mvc, mapper, token, "Finalization Test " + System.nanoTime());

        String creatorABody = mvc.perform(auth(put("/api/ordertracker/groups/" + groupId + "/creators/me"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"baseLocation":"Bangalore","hoursAvailablePerDay":4}"""))
                .andReturn().getResponse().getContentAsString();
        String creatorAId = mapper.readTree(creatorABody).get("id").asText();

        String customerBody = mvc.perform(auth(post("/api/ordertracker/groups/" + groupId + "/customers"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Priya Sharma","acquisitionChannel":"INSTAGRAM"}"""))
                .andReturn().getResponse().getContentAsString();
        String customerId = mapper.readTree(customerBody).get("id").asText();

        String orderBody = mvc.perform(auth(post("/api/ordertracker/groups/" + groupId + "/orders"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId":"%s","orderType":"INDIVIDUAL","createdByCreatorId":"%s",
                                 "itemName":"Sunflower amigurumi keychain",
                                 "orderReceivedDate":"2026-01-01T00:00:00Z",
                                 "mandatoryItems":[{"kind":"YARN","value":"Yellow","quantity":1,"unitCost":120}],
                                 "craftingTimeHours":4}""".formatted(customerId, creatorAId)))
                .andReturn().getResponse().getContentAsString();
        orderId = mapper.readTree(orderBody).get("id").asText();

        User userB = users.save(User.builder().name("Finalization B").email("finalizationtest-b@ot.test")
                .passwordHash("x").role(Role.USER).status(AccountStatus.ACTIVE).handle("finalizationtestb").build());
        groups.findById(groupId).ifPresent(g -> {
            g.getMemberIds().add(userB.getId());
            groups.save(g);
        });
        tokenB = jwt.issue(userB);
    }

    private String finalizationUrl(String suffix) {
        return "/api/ordertracker/groups/" + groupId + "/orders/" + orderId + "/finalization" + suffix;
    }

    @Test
    void requiresEveryCurrentMemberToApproveBeforeFinalizing() throws Exception {
        mvc.perform(auth(post(finalizationUrl("/propose")), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"finalCost\":250.0,\"finalRevenue\":400.0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.approvedByUserIds.length()").value(1));

        mvc.perform(get(finalizationUrl("")).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));

        String resolvedBody = mvc.perform(auth(post(finalizationUrl("/approve")), tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FINALIZED"))
                .andExpect(jsonPath("$.finalCost").value(250.0))
                .andExpect(jsonPath("$.finalRevenue").value(400.0))
                .andExpect(jsonPath("$.finalProfit").value(150.0))
                .andReturn().getResponse().getContentAsString();
        JsonNode resolved = mapper.readTree(resolvedBody);
        org.assertj.core.api.Assertions.assertThat(resolved.get("finalizedAt").isNull()).isFalse();
    }

    /** ad-4: proposing notifies the other member (not the proposer) so they know a
     *  finalization is waiting on them instead of having to check the order themselves. */
    @Test
    void proposingNotifiesTheOtherMemberButNotTheProposer() throws Exception {
        String proposerId = mapper.readTree(mvc.perform(auth(get("/api/auth/me"), token))
                .andReturn().getResponse().getContentAsString()).get("id").asText();
        String otherId = mapper.readTree(mvc.perform(auth(get("/api/auth/me"), tokenB))
                .andReturn().getResponse().getContentAsString()).get("id").asText();

        mvc.perform(auth(post(finalizationUrl("/propose")), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"finalCost\":250.0,\"finalRevenue\":400.0}"))
                .andExpect(status().isOk());

        org.assertj.core.api.Assertions.assertThat(notifications.findByUserIdOrderByCreatedAtDesc(otherId))
                .anySatisfy(n -> org.assertj.core.api.Assertions.assertThat(n.getType())
                        .isEqualTo(com.backlogtracker.commons.notification.domain.NotificationType.ORDER_FINALIZATION_PROPOSED));
        org.assertj.core.api.Assertions.assertThat(notifications.findByUserIdOrderByCreatedAtDesc(proposerId))
                .noneMatch(n -> n.getType() == com.backlogtracker.commons.notification.domain.NotificationType.ORDER_FINALIZATION_PROPOSED);
    }

    @Test
    void aSingleRejectionCancelsTheProposalAndAllowsReproposing() throws Exception {
        mvc.perform(auth(post(finalizationUrl("/propose")), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"finalCost\":250.0,\"finalRevenue\":400.0}"))
                .andExpect(status().isOk());

        mvc.perform(auth(post(finalizationUrl("/reject")), tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NONE"));

        mvc.perform(auth(post(finalizationUrl("/propose")), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"finalCost\":260.0,\"finalRevenue\":420.0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void aSecondProposalIsRejectedWhileOneIsAlreadyPending() throws Exception {
        mvc.perform(auth(post(finalizationUrl("/propose")), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"finalCost\":250.0,\"finalRevenue\":400.0}"))
                .andExpect(status().isOk());

        mvc.perform(auth(post(finalizationUrl("/propose")), tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"finalCost\":999.0,\"finalRevenue\":999.0}"))
                .andExpect(status().isConflict());
    }

    @Test
    void aMemberLeavingMidApprovalInvalidatesTheProposalAndNotifiesTheProposer() throws Exception {
        mvc.perform(auth(post(finalizationUrl("/propose")), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"finalCost\":250.0,\"finalRevenue\":400.0}"))
                .andExpect(status().isOk());

        mvc.perform(auth(delete("/api/groups/" + groupId + "/members/me"), tokenB))
                .andExpect(status().isNoContent());

        mvc.perform(get(finalizationUrl("")).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NONE"));

        boolean notified = notifications.findAll().stream()
                .anyMatch(n -> n.getType() == com.backlogtracker.commons.notification.domain.NotificationType.ORDER_FINALIZATION_INVALIDATED);
        org.assertj.core.api.Assertions.assertThat(notified).isTrue();
    }
}
