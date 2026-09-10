package com.backlogtracker.notification;

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

import com.backlogtracker.group.repository.GroupRepository;
import com.backlogtracker.notification.repository.NotificationRepository;
import com.backlogtracker.security.JwtService;
import com.backlogtracker.support.AuthTestSupport;
import com.backlogtracker.user.domain.AccountStatus;
import com.backlogtracker.user.domain.Role;
import com.backlogtracker.user.domain.User;
import com.backlogtracker.user.repository.UserRepository;
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
