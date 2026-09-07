package com.backlogtracker.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;

/** Shared helper: log in as the seeded dev user and return a bearer token. */
public final class AuthTestSupport {

    private AuthTestSupport() {
    }

    public static String devToken(MockMvc mvc, ObjectMapper mapper) throws Exception {
        String body = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"test123","password":"test123"}"""))
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(body).get("token").asText();
    }
}
