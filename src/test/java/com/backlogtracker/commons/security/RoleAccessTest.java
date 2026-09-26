package com.backlogtracker.commons.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.backlogtracker.backlogtracker.config.repository.ConfigHistoryRepository;
import com.backlogtracker.backlogtracker.config.repository.ConfigRepository;
import com.backlogtracker.support.AuthTestSupport;
import com.backlogtracker.commons.user.domain.AccountStatus;
import com.backlogtracker.commons.user.domain.Role;
import com.backlogtracker.commons.user.domain.User;
import com.backlogtracker.commons.user.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class RoleAccessTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired UserRepository users;
    @Autowired JwtService jwt;
    @Autowired ConfigRepository configRepo;
    @Autowired ConfigHistoryRepository historyRepo;

    private String userToken;

    /** This class both reads and PUTs the shared AppConfig singleton — round 6 review
     *  finding: without resetting it, a real mutation here (or one left behind by a
     *  different @SpringBootTest class sharing the same Mongo instance, e.g. InsightsApiTest
     *  adding an "Urgent" priority) leaks into whichever test happens to run next in the
     *  same JVM. getConfig() reseeds clean defaults on its own the moment the doc is
     *  missing, so a plain deleteAll() is enough — no need to hand-construct the default. */
    @BeforeEach
    void setUp() {
        configRepo.deleteAll();
        historyRepo.deleteAll();
        users.findByEmailIgnoreCase("plainuser@demo.test").ifPresent(users::delete);
        User u = users.save(User.builder()
                .name("Uma User").email("plainuser@demo.test")
                .passwordHash("x").role(Role.USER).status(AccountStatus.ACTIVE)
                .handle("uma").build());
        userToken = jwt.issue(u);
    }

    @AfterEach
    void tearDown() {
        users.findByEmailIgnoreCase("plainuser@demo.test").ifPresent(users::delete);
        configRepo.deleteAll();
        historyRepo.deleteAll();
    }

    private static final String CONFIG = """
            {"priorityWeight":0.5,"urgencyWeight":0.3,"effortWeight":0.2,
             "urgencyWindowDays":14,"staleThresholdDays":10,"buriedThresholdDays":30,
             "defaultDueDateOffsetDays":30,"effortCapDays":30,"maxGroupsPerUser":5,
             "buriedPriorityLevels":["Low"],
             "priorityValues":{"Critical":4,"High":3,"Medium":2,"Low":1}}""";

    @Test
    void plainUserCanReadAndMutateItemsButNotAdminEndpoints() throws Exception {
        String groupId = AuthTestSupport.createGroup(mvc, mapper, userToken, "Uma Group");

        mvc.perform(get("/api/items").param("groupId", groupId)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk());
        mvc.perform(post("/api/items").header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"groupId":"%s","title":"role check","category":"Project","priority":"Low",
                                 "effortEstimate":{"value":1,"unit":"HOURS"}}""".formatted(groupId)))
                .andExpect(status().isCreated());

        mvc.perform(put("/api/config").header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON).content(CONFIG))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanHitAdminEndpoints() throws Exception {
        String admin = AuthTestSupport.devToken(mvc, mapper); // test123 is ADMIN
        mvc.perform(put("/api/config").header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON).content(CONFIG))
                .andExpect(status().isOk());
    }
}
