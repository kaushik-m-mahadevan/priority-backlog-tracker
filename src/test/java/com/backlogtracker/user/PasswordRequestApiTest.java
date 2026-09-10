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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import com.backlogtracker.support.AuthTestSupport;
import com.backlogtracker.user.domain.AccountStatus;
import com.backlogtracker.user.domain.Role;
import com.backlogtracker.user.domain.User;
import com.backlogtracker.user.repository.PasswordRequestRepository;
import com.backlogtracker.user.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class PasswordRequestApiTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired UserRepository users;
    @Autowired PasswordRequestRepository requests;
    @Autowired PasswordEncoder encoder;

    private String adminToken;
    private String userToken;
    private String userId;

    @BeforeEach
    void setUp() throws Exception {
        requests.deleteAll();
        users.findByEmailIgnoreCase("pw.user@demo.test").ifPresent(users::delete);
        adminToken = AuthTestSupport.devToken(mvc, mapper);

        User u = users.save(User.builder().name("Pat").email("pw.user@demo.test")
                .passwordHash(encoder.encode("origpass1")).role(Role.USER)
                .status(AccountStatus.ACTIVE).handle("pat").build());
        userId = u.getId();
        userToken = login("pw.user@demo.test", "origpass1");
    }

    private String login(String email, String pw) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, pw)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(body).get("token").asText();
    }

    @Test
    void changeRequestIsHeldUntilAdminApprovesThenApplies() throws Exception {
        mvc.perform(post("/api/auth/password-change").header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"origpass1\",\"newPassword\":\"brandnew2\"}"))
                .andExpect(status().isAccepted());

        // not applied yet — old password still works, new one does not
        login("pw.user@demo.test", "origpass1");
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"pw.user@demo.test\",\"password\":\"brandnew2\"}"))
                .andExpect(status().isUnauthorized());

        String list = mvc.perform(get("/api/admin/password-requests").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value("CHANGE"))
                .andReturn().getResponse().getContentAsString();
        String reqId = mapper.readTree(list).get(0).get("id").asText();

        mvc.perform(post("/api/admin/password-requests/" + reqId + "/approve")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        // now the new password works
        login("pw.user@demo.test", "brandnew2");
    }

    @Test
    void wrongCurrentPasswordIs400() throws Exception {
        mvc.perform(post("/api/auth/password-change").header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"nope\",\"newPassword\":\"brandnew2\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void onlyOnePendingRequestPerUser() throws Exception {
        String ok = "{\"currentPassword\":\"origpass1\",\"newPassword\":\"brandnew2\"}";
        mvc.perform(post("/api/auth/password-change").header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON).content(ok))
                .andExpect(status().isAccepted());
        mvc.perform(post("/api/auth/password-change").header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON).content(ok))
                .andExpect(status().isConflict());
    }

    @Test
    void forgotPasswordIsAlwaysAcceptedAndAdminSetsATempPassword() throws Exception {
        mvc.perform(post("/api/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"pw.user@demo.test\"}"))
                .andExpect(status().isAccepted());
        // unknown address also 202 — no account probing
        mvc.perform(post("/api/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ghost@demo.test\"}"))
                .andExpect(status().isAccepted());

        String list = mvc.perform(get("/api/admin/password-requests").header("Authorization", "Bearer " + adminToken))
                .andReturn().getResponse().getContentAsString();
        String reqId = mapper.readTree(list).get(0).get("id").asText();

        mvc.perform(post("/api/admin/password-requests/" + reqId + "/approve")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"temporaryPassword\":\"temppass9\"}"))
                .andExpect(status().isNoContent());

        login("pw.user@demo.test", "temppass9");
    }

    @Test
    void nonAdminCannotSeeOrDecidePasswordRequests() throws Exception {
        mvc.perform(get("/api/admin/password-requests").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void userCanChangeTheirDisplayNameButThisEndpointNeedsAuth() throws Exception {
        mvc.perform(patch("/api/users/me").header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Patricia\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Patricia"))
                .andExpect(jsonPath("$.email").value("pw.user@demo.test"));
        mvc.perform(patch("/api/users/me").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"X\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void animationsPreferenceTogglesAndShowsUpOnMe() throws Exception {
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + userToken))
                .andExpect(jsonPath("$.animationsEnabled").value(false));

        mvc.perform(patch("/api/users/me").header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"animationsEnabled\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.animationsEnabled").value(true))
                .andExpect(jsonPath("$.name").value("Pat")); // name untouched

        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + userToken))
                .andExpect(jsonPath("$.animationsEnabled").value(true));
    }
}
