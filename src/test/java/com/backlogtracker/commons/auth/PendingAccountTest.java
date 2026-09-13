package com.backlogtracker.commons.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import com.backlogtracker.commons.security.JwtService;
import com.backlogtracker.commons.user.domain.AccountStatus;
import com.backlogtracker.commons.user.domain.Role;
import com.backlogtracker.commons.user.domain.User;
import com.backlogtracker.commons.user.repository.UserRepository;

@SpringBootTest
@AutoConfigureMockMvc
class PendingAccountTest {

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired JwtService jwt;

    private String token;

    @BeforeEach
    void setUp() {
        users.findByEmailIgnoreCase("pending@demo.test").ifPresent(users::delete);
        User u = users.save(User.builder()
                .name("Percy Pending").email("pending@demo.test")
                .passwordHash("x").role(Role.USER).status(AccountStatus.PENDING)
                .handle("pcy").build());
        token = jwt.issue(u);
    }

    @AfterEach
    void tearDown() {
        users.findByEmailIgnoreCase("pending@demo.test").ifPresent(users::delete);
    }

    @Test
    void pendingAccountIsBlockedFromTheAppButCanReadItsOwnStatus() throws Exception {
        mvc.perform(get("/api/items").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value("PENDING"));

        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.email").value("pending@demo.test"));
    }
}
