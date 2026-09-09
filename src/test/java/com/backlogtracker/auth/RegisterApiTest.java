package com.backlogtracker.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.backlogtracker.user.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class RegisterApiTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired UserRepository users;

    @AfterEach
    void cleanUp() {
        for (String e : new String[] {"reg@demo.test", "reg2@demo.test"}) {
            users.findByEmailIgnoreCase(e).ifPresent(users::delete);
        }
    }

    private MockHttpServletRequestBuilder register(String name, String handle, String email,
                                                  String password) {
        return post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"%s","handle":"%s","email":"%s","password":"%s"}"""
                        .formatted(name, handle, email, password));
    }

    @Test
    void registeringYieldsAPendingAccountThatIsGatedFromTheApp() throws Exception {
        String body = mvc.perform(register("Reg One", "regone", "reg@demo.test", "changeme123"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.user.role").value("USER"))
                .andExpect(jsonPath("$.user.status").value("PENDING"))
                .andReturn().getResponse().getContentAsString();
        String token = mapper.readTree(body).get("token").asText();

        mvc.perform(get("/api/items").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value("PENDING"));
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void rejectsDuplicateEmailAndHandle() throws Exception {
        mvc.perform(register("Reg One", "dupe", "reg@demo.test", "changeme123"))
                .andExpect(status().isCreated());
        mvc.perform(register("Reg Two", "dupe2", "reg@demo.test", "changeme123"))
                .andExpect(status().isConflict());
        mvc.perform(register("Reg Two", "dupe", "reg2@demo.test", "changeme123"))
                .andExpect(status().isConflict());
    }

    @Test
    void rejectsABadHandleOrShortPassword() throws Exception {
        mvc.perform(register("X", "AB", "reg@demo.test", "changeme123"))
                .andExpect(status().isBadRequest());
        mvc.perform(register("X", "has space", "reg@demo.test", "changeme123"))
                .andExpect(status().isBadRequest());
        mvc.perform(register("X", "Upper", "reg@demo.test", "changeme123"))
                .andExpect(status().isBadRequest());
        mvc.perform(register("X", "okhandle", "reg@demo.test", "short"))
                .andExpect(status().isBadRequest());
    }
}
