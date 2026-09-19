package com.backlogtracker.commons.notification;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.backlogtracker.commons.group.repository.GroupRepository;
import com.backlogtracker.commons.notification.repository.NotificationRepository;
import com.backlogtracker.commons.security.JwtService;
import com.backlogtracker.support.AuthTestSupport;
import com.backlogtracker.commons.user.domain.AccountStatus;
import com.backlogtracker.commons.user.domain.Role;
import com.backlogtracker.commons.user.domain.User;
import com.backlogtracker.commons.user.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class NotificationApiTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired UserRepository users;
    @Autowired GroupRepository groups;
    @Autowired NotificationRepository notifications;
    @Autowired JwtService jwt;

    private String annToken;
    private String boToken;
    private String groupId;

    @BeforeEach
    void setUp() throws Exception {
        cleanUp();
        AuthTestSupport.devToken(mvc, mapper);
        User ann = users.save(User.builder().name("Ann").email("ann@n.test")
                .passwordHash("x").role(Role.USER).status(AccountStatus.ACTIVE).handle("ann").build());
        User bo = users.save(User.builder().name("Bo").email("bo@n.test")
                .passwordHash("x").role(Role.USER).status(AccountStatus.ACTIVE).handle("bo").build());
        annToken = jwt.issue(ann);
        boToken = jwt.issue(bo);
        groupId = AuthTestSupport.createGroup(mvc, mapper, annToken, "Ann Group");
    }

    @AfterEach
    void cleanUp() {
        notifications.deleteAll();
        groups.deleteAll();
        for (String e : new String[] {"ann@n.test", "bo@n.test"}) {
            users.findByEmailIgnoreCase(e).ifPresent(users::delete);
        }
    }

    private void invite(String token, String to, int expectedStatus) throws Exception {
        mvc.perform(post("/api/groups/" + groupId + "/invites")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"to\":\"" + to + "\"}"))
                .andExpect(status().is(expectedStatus));
    }

    @Test
    void inviteByHandleThenAcceptJoinsTheGroup() throws Exception {
        invite(annToken, "bo", 201);

        JsonNode inbox = mapper.readTree(mvc.perform(get("/api/notifications")
                        .header("Authorization", "Bearer " + boToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pending").value(1))
                .andExpect(jsonPath("$.items[0].type").value("GROUP_INVITE"))
                .andExpect(jsonPath("$.items[0].groupName").value("Ann Group"))
                .andExpect(jsonPath("$.items[0].invitedByName").value("Ann"))
                .andReturn().getResponse().getContentAsString());
        String nid = inbox.get("items").get(0).get("id").asText();

        mvc.perform(post("/api/notifications/" + nid + "/accept")
                        .header("Authorization", "Bearer " + boToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));

        // Bo is now in the group
        mvc.perform(get("/api/groups").header("Authorization", "Bearer " + boToken))
                .andExpect(jsonPath("$[0].id").value(groupId))
                .andExpect(jsonPath("$[0].members.length()").value(2));
    }

    @Test
    void inviteByEmailCanBeDeclined() throws Exception {
        invite(annToken, "bo@n.test", 201);
        String nid = notifications.findByUserIdOrderByCreatedAtDesc(
                users.findByEmailIgnoreCase("bo@n.test").orElseThrow().getId()).get(0).getId();

        mvc.perform(post("/api/notifications/" + nid + "/decline")
                        .header("Authorization", "Bearer " + boToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DECLINED"));

        mvc.perform(get("/api/groups").header("Authorization", "Bearer " + boToken))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void rejectsNonMemberInviter_unknownTarget_alreadyMember_andDuplicates() throws Exception {
        invite(boToken, "ann", 403);              // Bo isn't in the group
        invite(annToken, "ghost", 404);           // no such handle
        invite(annToken, "ann", 409);             // Ann is already a member
        invite(annToken, "bo", 201);
        invite(annToken, "bo", 409);              // duplicate pending invite
    }

    /** Regression test: the per-applet group cap is only checked at invite time — if the
     *  invitee later fills up their remaining slots before accepting, addMember() must
     *  re-check the cap so acceptance fails with a clear error for the invitee, rather
     *  than silently pushing them over the limit. */
    @Test
    void acceptingAnInviteReChecksTheMembershipCapEvenIfItWasFineAtInviteTime() throws Exception {
        invite(annToken, "bo", 201);
        String nid = notifications.findByUserIdOrderByCreatedAtDesc(
                users.findByEmailIgnoreCase("bo@n.test").orElseThrow().getId()).get(0).getId();

        // Backlog Tracker's default cap is 5 — Bo fills all 5 slots with other groups
        // between being invited and accepting.
        for (int i = 0; i < 5; i++) {
            AuthTestSupport.createGroup(mvc, mapper, boToken, "Bo's group " + i);
        }

        mvc.perform(post("/api/notifications/" + nid + "/accept")
                        .header("Authorization", "Bearer " + boToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("maximum of 5 groups")));

        // Rejected, not silently joined.
        mvc.perform(get("/api/groups").header("Authorization", "Bearer " + annToken))
                .andExpect(jsonPath("$[0].members.length()").value(1));
    }

    @Test
    void pendingInvitesListsWhoWasInvitedByWhomAndDropsOffOnceAccepted() throws Exception {
        invite(annToken, "bo", 201);

        mvc.perform(get("/api/groups/" + groupId + "/invites").header("Authorization", "Bearer " + annToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].invitedDisplay").value("@bo"))
                .andExpect(jsonPath("$[0].invitedByName").value("Ann"));

        String nid = notifications.findByUserIdOrderByCreatedAtDesc(
                users.findByEmailIgnoreCase("bo@n.test").orElseThrow().getId()).get(0).getId();
        mvc.perform(post("/api/notifications/" + nid + "/accept").header("Authorization", "Bearer " + boToken))
                .andExpect(status().isOk());

        mvc.perform(get("/api/groups/" + groupId + "/invites").header("Authorization", "Bearer " + annToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void cannotAcceptSomeoneElsesNotification() throws Exception {
        invite(annToken, "bo", 201);
        String nid = notifications.findByUserIdOrderByCreatedAtDesc(
                users.findByEmailIgnoreCase("bo@n.test").orElseThrow().getId()).get(0).getId();
        mvc.perform(post("/api/notifications/" + nid + "/accept")
                        .header("Authorization", "Bearer " + annToken))
                .andExpect(status().isForbidden());
    }
}
