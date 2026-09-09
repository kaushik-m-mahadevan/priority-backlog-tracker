package com.backlogtracker.user;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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

import com.backlogtracker.security.JwtService;
import com.backlogtracker.support.AuthTestSupport;
import com.backlogtracker.user.domain.Role;
import com.backlogtracker.user.domain.User;
import com.backlogtracker.user.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class UserApiTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired UserRepository users;
    @Autowired JwtService jwt;

    private String ownerToken;

    @BeforeEach
    void setUp() throws Exception {
        for (String e : new String[] {"priya@founders.test", "sam@founders.test", "vic@founders.test"}) {
            users.findByEmailIgnoreCase(e).ifPresent(users::delete);
        }
        ownerToken = AuthTestSupport.devToken(mvc, mapper); // test123 is an OWNER
    }

    @Test
    void listsTeamForAuthedCaller() throws Exception {
        mvc.perform(get("/api/users").header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.email=='test123')].role")
                        .value(org.hamcrest.Matchers.hasItem("ADMIN")))
                .andExpect(jsonPath("$[0].name").exists());
    }

    @Test
    void requiresAuth() throws Exception {
        mvc.perform(get("/api/users")).andExpect(status().isUnauthorized());
    }

    @Test
    void ownerCreatesAMemberThenChangesTheirRole() throws Exception {
        String body = """
                {"name":"Priya Shah","email":"priya@founders.test","password":"changeme123"}""";
        JsonNode created = mapper.readTree(mvc.perform(post("/api/users")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("USER"))
                .andReturn().getResponse().getContentAsString());

        // the new member can sign in
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"priya@founders.test","password":"changeme123"}"""))
                .andExpect(status().isOk());

        mvc.perform(patch("/api/users/" + created.get("id").asText())
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"role":"ADMIN"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    void rejectsADuplicateEmail() throws Exception {
        String body = """
                {"name":"Sam Lee","email":"sam@founders.test","password":"changeme123"}""";
        mvc.perform(post("/api/users").header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/users").header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void nonOwnerCannotCreateMembers() throws Exception {
        User viewer = users.save(User.builder()
                .name("Vic Viewer").email("vic@founders.test")
                .passwordHash("x").role(Role.USER).userCode("VIC2").build());
        try {
            mvc.perform(post("/api/users").header("Authorization", "Bearer " + jwt.issue(viewer))
                            .contentType(MediaType.APPLICATION_JSON).content("""
                                    {"name":"X","email":"x@founders.test","password":"changeme123"}"""))
                    .andExpect(status().isForbidden());
        } finally {
            users.delete(viewer);
        }
    }
}
