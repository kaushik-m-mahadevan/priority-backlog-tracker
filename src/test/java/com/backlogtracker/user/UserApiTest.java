package com.backlogtracker.user;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import com.backlogtracker.support.AuthTestSupport;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class UserApiTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    @Test
    void listsActiveUsersForAnAuthedCaller() throws Exception {
        String token = AuthTestSupport.devToken(mvc, mapper);
        mvc.perform(get("/api/users").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.email=='test123')].role")
                        .value(org.hamcrest.Matchers.hasItem("ADMIN")))
                .andExpect(jsonPath("$[0].name").exists())
                .andExpect(jsonPath("$[0].handle").exists());
    }

    @Test
    void requiresAuth() throws Exception {
        mvc.perform(get("/api/users")).andExpect(status().isUnauthorized());
    }
}
