package com.backlogtracker.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;

/** Shared helpers: a bearer token for the bootstrap admin, and a group it owns. */
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

    /** Creates a fresh group owned by the given token's user and returns its id. */
    public static String createGroup(MockMvc mvc, ObjectMapper mapper, String token, String name)
            throws Exception {
        String body = mvc.perform(post("/api/groups")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\"}"))
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(body).get("id").asText();
    }
}
