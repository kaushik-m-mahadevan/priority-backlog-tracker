package com.backlogtracker.user;

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

import com.backlogtracker.support.AuthTestSupport;
import com.backlogtracker.user.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class AdminApiTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired UserRepository users;

    private String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        cleanUp();
        adminToken = AuthTestSupport.devToken(mvc, mapper); // test123 is ADMIN
    }

    @AfterEach
    void cleanUp() {
        for (String e : new String[] {"cand@demo.test", "cand2@demo.test"}) {
            users.findByEmailIgnoreCase(e).ifPresent(users::delete);
        }
    }

    private String registerAndToken(String handle, String email) throws Exception {
        String body = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Cand","handle":"%s","email":"%s","password":"changeme123"}"""
                                .formatted(handle, email)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(body).get("token").asText();
    }

    private String idOf(String email) {
        return users.findByEmailIgnoreCase(email).orElseThrow().getId();
    }

    @Test
    void adminApprovesAPendingUserWhoCanThenUseTheApp() throws Exception {
        String candToken = registerAndToken("cand", "cand@demo.test");

        mvc.perform(get("/api/admin/pending-users").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.email=='cand@demo.test')].status")
                        .value(org.hamcrest.Matchers.hasItem("PENDING")));

        mvc.perform(post("/api/admin/users/" + idOf("cand@demo.test") + "/approve")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        // the freshly-approved account is no longer gated
        mvc.perform(post("/api/groups").header("Authorization", "Bearer " + candToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Cand Group\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void adminRejectsAPendingUserFreeingTheEmail() throws Exception {
        registerAndToken("cand2", "cand2@demo.test");

        mvc.perform(post("/api/admin/users/" + idOf("cand2@demo.test") + "/reject")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        // email can be registered again
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Cand","handle":"cand2b","email":"cand2@demo.test",
                                 "password":"changeme123"}"""))
                .andExpect(status().isCreated());
    }

    @Test
    void plainUserCannotReachTheConsole() throws Exception {
        String candToken = registerAndToken("cand", "cand@demo.test");
        users.findByEmailIgnoreCase("cand@demo.test").ifPresent(u -> {
            u.setStatus(com.backlogtracker.user.domain.AccountStatus.ACTIVE);
            users.save(u);
        });
        mvc.perform(get("/api/admin/pending-users").header("Authorization", "Bearer " + candToken))
                .andExpect(status().isForbidden());
    }
}
