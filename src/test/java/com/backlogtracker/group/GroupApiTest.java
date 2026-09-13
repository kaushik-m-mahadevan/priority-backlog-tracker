package com.backlogtracker.group;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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

import com.backlogtracker.config.service.ConfigService;
import com.backlogtracker.group.repository.GroupRepository;
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
class GroupApiTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired UserRepository users;
    @Autowired GroupRepository groups;
    @Autowired JwtService jwt;
    @Autowired ConfigService configService;

    private String tokenA;
    private String tokenB;
    private String userAId;

    @BeforeEach
    void setUp() throws Exception {
        cleanUp();
        AuthTestSupport.devToken(mvc, mapper); // ensure the bootstrap admin exists
        User a = users.save(User.builder().name("Ann").email("ann@grp.test")
                .passwordHash("x").role(Role.USER).status(AccountStatus.ACTIVE).handle("ann").build());
        User b = users.save(User.builder().name("Bo").email("bo@grp.test")
                .passwordHash("x").role(Role.USER).status(AccountStatus.ACTIVE).handle("bo").build());
        userAId = a.getId();
        tokenA = jwt.issue(a);
        tokenB = jwt.issue(b);
    }

    @AfterEach
    void cleanUp() {
        groups.findByMemberIdsContaining(userAId == null ? "-" : userAId).forEach(groups::delete);
        for (String e : new String[] {"ann@grp.test", "bo@grp.test"}) {
            users.findByEmailIgnoreCase(e).ifPresent(users::delete);
        }
        groups.deleteAll();
    }

    private JsonNode createGroup(String token, String name) throws Exception {
        String body = mvc.perform(post("/api/groups").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(body);
    }

    @Test
    void createsAGroupWithTheCreatorAsSoleMemberAndListsOnlyMine() throws Exception {
        JsonNode group = createGroup(tokenA, "Ann Workspace");

        mvc.perform(get("/api/groups").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Ann Workspace"))
                .andExpect(jsonPath("$[0].members.length()").value(1))
                .andExpect(jsonPath("$[0].members[0].handle").value("ann"));

        // categories are Backlog Tracker's own data, not part of the commons GroupView —
        // a brand-new group falls back to the shared defaults until it edits them
        mvc.perform(get("/api/groups/" + group.get("id").asText() + "/categories")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(jsonPath("$.categories", org.hamcrest.Matchers.hasItem("Research")));

        // Bo sees none of Ann's groups
        mvc.perform(get("/api/groups").header("Authorization", "Bearer " + tokenB))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void enforcesTheGroupsPerUserCap() throws Exception {
        var cfg = configService.getConfig();
        int cap = cfg.getMaxGroupsPerUser();
        for (int i = 0; i < cap; i++) {
            createGroup(tokenA, "G" + i);
        }
        mvc.perform(post("/api/groups").header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"one too many\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void nonMemberCannotReadAGroup() throws Exception {
        String id = createGroup(tokenA, "Private").get("id").asText();
        mvc.perform(get("/api/groups/" + id).header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isForbidden());
    }

    @Test
    void anyMemberCanRenameTheGroupButNonMembersCannot() throws Exception {
        String id = createGroup(tokenA, "Old Name").get("id").asText();

        mvc.perform(patch("/api/groups/" + id).header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"New Name\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("New Name"));

        mvc.perform(get("/api/groups/" + id).header("Authorization", "Bearer " + tokenA))
                .andExpect(jsonPath("$.name").value("New Name"));

        // non-member is rejected
        mvc.perform(patch("/api/groups/" + id).header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Hijacked\"}"))
                .andExpect(status().isForbidden());

        // blank name is rejected
        mvc.perform(patch("/api/groups/" + id).header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"   \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void leavingAsTheSoleMemberDeletesTheGroup() throws Exception {
        String id = createGroup(tokenA, "Solo").get("id").asText();
        mvc.perform(delete("/api/groups/" + id + "/members/me")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/groups/" + id).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNotFound());
    }
}
