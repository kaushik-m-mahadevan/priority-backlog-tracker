package com.backlogtracker.ordertracker.master;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import com.backlogtracker.commons.notification.domain.NotificationType;
import com.backlogtracker.commons.notification.repository.NotificationRepository;
import com.backlogtracker.commons.security.JwtService;
import com.backlogtracker.commons.user.domain.AccountStatus;
import com.backlogtracker.commons.user.domain.Role;
import com.backlogtracker.commons.user.domain.User;
import com.backlogtracker.commons.user.repository.UserRepository;
import com.backlogtracker.ordertracker.master.repository.CostConfigChangeRequestRepository;
import com.backlogtracker.support.AuthTestSupport;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class CostConfigChangeApiTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired UserRepository users;
    @Autowired GroupRepository groups;
    @Autowired JwtService jwt;
    @Autowired CostConfigChangeRequestRepository changeRequests;
    @Autowired NotificationRepository notifications;

    private String proposerToken;
    private String proposerId;
    private String groupId;

    @BeforeEach
    void setUp() throws Exception {
        changeRequests.deleteAll();
        users.findByEmailIgnoreCase("ccc-member2@ot.test").ifPresent(users::delete);
        users.findByEmailIgnoreCase("ccc-member3@ot.test").ifPresent(users::delete);
        // the bootstrap admin's group membership accumulates across test classes sharing the
        // embedded Mongo — without this, a full-suite run can hit the 5-group cap and fail here.
        groups.deleteAll();
        proposerToken = AuthTestSupport.devToken(mvc, mapper);
        proposerId = mapper.readTree(mvc.perform(auth(get("/api/auth/me"), proposerToken))
                .andReturn().getResponse().getContentAsString()).get("id").asText();
        groupId = AuthTestSupport.createGroup(mvc, mapper, proposerToken, "CCC Test " + System.nanoTime());
        notifications.deleteAll();
    }

    private MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder b, String t) {
        return b.header("Authorization", "Bearer " + t);
    }

    private String addMember(String email, String handle) {
        User member = users.save(User.builder().name(handle).email(email)
                .passwordHash("x").role(Role.USER).status(AccountStatus.ACTIVE).handle(handle).build());
        groups.findById(groupId).ifPresent(g -> {
            g.getMemberIds().add(member.getId());
            groups.save(g);
        });
        return jwt.issue(member);
    }

    @Test
    void aMemberLeavingMidApprovalInvalidatesThePendingRequestAndNotifiesTheProposer() throws Exception {
        // a third member stays put throughout, so the group never shrinks to a lone
        // proposer — the re-propose assertion at the end genuinely exercises "invalidated
        // doesn't block a new proposal", not just "solo groups auto-resolve."
        addMember("ccc-member3@ot.test", "cccmember3");
        String member2Token = addMember("ccc-member2@ot.test", "cccmember2");

        String proposeBody = mvc.perform(auth(post(
                        "/api/ordertracker/groups/" + groupId + "/business-config/change-requests"), proposerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"overheadPercentage":0.18,"profitMarginPercentage":0.25}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn().getResponse().getContentAsString();
        String requestId = mapper.readTree(proposeBody).get("id").asText();

        // the second (non-approving) member leaves instead of voting
        mvc.perform(auth(delete("/api/groups/" + groupId + "/members/me"), member2Token))
                .andExpect(status().isNoContent());

        assertThat(changeRequests.findById(requestId).orElseThrow().getStatus().name())
                .isEqualTo("INVALIDATED");

        assertThat(notifications.findByUserIdOrderByCreatedAtDesc(proposerId))
                .anySatisfy(n -> assertThat(n.getType()).isEqualTo(NotificationType.COST_CONFIG_INVALIDATED));

        // propose() only refuses while a request is still PENDING — invalidated doesn't
        // block a new one, and with member3 still present + not yet approving, this one
        // correctly stays PENDING rather than auto-resolving.
        mvc.perform(auth(post(
                        "/api/ordertracker/groups/" + groupId + "/business-config/change-requests"), proposerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"overheadPercentage":0.20,"profitMarginPercentage":0.30}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void aSoloProposerAutoResolvesImmediatelyAndTheSummaryReflectsItWithoutAReload() throws Exception {
        mvc.perform(auth(post(
                        "/api/ordertracker/groups/" + groupId + "/business-config/change-requests"), proposerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"overheadPercentage":0.22,"profitMarginPercentage":0.28}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        mvc.perform(auth(get("/api/ordertracker/groups/" + groupId + "/business-config"), proposerToken))
                .andExpect(jsonPath("$.overheadPercentage").value(0.22))
                .andExpect(jsonPath("$.profitMarginPercentage").value(0.28));
    }

    /** Regression test: the setup wizard's overhead/margin-only step resends the current
     *  (still-default) hourly wage untouched on every propose — that must NOT silently mark
     *  the wage as "confirmed" (which would hide the default-rate warning without the group
     *  ever actually agreeing on a real number). Only a propose that actually changes the
     *  wage should flip it. */
    @Test
    void resendingTheSameHourlyWageDoesNotConfirmItButChangingItDoes() throws Exception {
        double seededWage = mapper.readTree(mvc.perform(auth(
                        get("/api/ordertracker/groups/" + groupId + "/business-config"), proposerToken))
                        .andExpect(jsonPath("$.hourlyWageConfirmed").value(false))
                        .andReturn().getResponse().getContentAsString())
                .get("hourlyWage").asDouble();

        mvc.perform(auth(post(
                        "/api/ordertracker/groups/" + groupId + "/business-config/change-requests"), proposerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"overheadPercentage":0.22,"profitMarginPercentage":0.28,"hourlyWage":%s}"""
                                .formatted(seededWage)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        mvc.perform(auth(get("/api/ordertracker/groups/" + groupId + "/business-config"), proposerToken))
                .andExpect(jsonPath("$.hourlyWageConfirmed").value(false));

        mvc.perform(auth(post(
                        "/api/ordertracker/groups/" + groupId + "/business-config/change-requests"), proposerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"overheadPercentage":0.22,"profitMarginPercentage":0.28,"hourlyWage":150}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        mvc.perform(auth(get("/api/ordertracker/groups/" + groupId + "/business-config"), proposerToken))
                .andExpect(jsonPath("$.hourlyWageConfirmed").value(true))
                .andExpect(jsonPath("$.hourlyWage").value(150.0));
    }
}
