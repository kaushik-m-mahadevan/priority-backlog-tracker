package com.backlogtracker.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.backlogtracker.support.AuthTestSupport;
import com.backlogtracker.user.domain.Role;
import com.backlogtracker.user.domain.User;
import com.backlogtracker.user.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class RoleAccessTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired UserRepository users;
    @Autowired JwtService jwt;

    private String viewerToken;

    @BeforeEach
    void setUp() {
        users.findByEmailIgnoreCase("viewer@demo.test").ifPresent(users::delete);
        User viewer = users.save(User.builder()
                .name("Vera Viewer").email("viewer@demo.test")
                .passwordHash("x").role(Role.VIEWER).userCode("VVW").build());
        viewerToken = jwt.issue(viewer);
    }

    private static final String NEW_ITEM = """
            {"title":"nope","category":"Project","priority":"Low",
             "effortEstimate":{"value":1,"unit":"HOURS"}}""";

    @Test
    void viewerCanReadButNotMutate() throws Exception {
        mvc.perform(get("/api/items").header("Authorization", "Bearer " + viewerToken))
                .andExpect(status().isOk());

        mvc.perform(post("/api/items").header("Authorization", "Bearer " + viewerToken)
                        .contentType(MediaType.APPLICATION_JSON).content(NEW_ITEM))
                .andExpect(status().isForbidden());
    }

    @Test
    void ownerCanMutate() throws Exception {
        String owner = AuthTestSupport.devToken(mvc, mapper); // test123 is an OWNER
        mvc.perform(post("/api/items").header("Authorization", "Bearer " + owner)
                        .contentType(MediaType.APPLICATION_JSON).content(NEW_ITEM))
                .andExpect(status().isCreated());
    }
}
