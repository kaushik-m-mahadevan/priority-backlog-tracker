package com.backlogtracker.commons.auth;

import static org.assertj.core.api.Assertions.assertThat;
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

import com.backlogtracker.commons.notification.domain.NotificationType;
import com.backlogtracker.commons.notification.repository.NotificationRepository;
import com.backlogtracker.commons.user.domain.Role;
import com.backlogtracker.commons.user.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class RegisterApiTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired UserRepository users;
    @Autowired NotificationRepository notifications;

    @AfterEach
    void cleanUp() {
        for (String e : new String[] {"reg@demo.test", "reg2@demo.test", "tiny@demo.test", "tiny2@demo.test"}) {
            users.findByEmailIgnoreCase(e).ifPresent(users::delete);
        }
    }

    private MockHttpServletRequestBuilder register(String name, String handle, String email,
                                                  String password) {
        return register(name, handle, email, password, password);
    }

    private MockHttpServletRequestBuilder register(String name, String handle, String email,
                                                  String password, String confirmPassword) {
        return post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"%s","handle":"%s","email":"%s","password":"%s","confirmPassword":"%s"}"""
                        .formatted(name, handle, email, password, confirmPassword));
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

    /** ad-4: registration notifies every admin so a new signup doesn't just sit unnoticed
     *  until someone happens to check Admin. */
    @Test
    void registeringNotifiesEveryAdmin() throws Exception {
        mvc.perform(register("Reg One", "regone", "reg@demo.test", "changeme123"))
                .andExpect(status().isCreated());

        for (var admin : users.findByRole(Role.ADMIN)) {
            assertThat(notifications.findByUserIdOrderByCreatedAtDesc(admin.getId()))
                    .anySatisfy(n -> assertThat(n.getType()).isEqualTo(NotificationType.SIGNUP_PENDING));
        }
    }

    @Test
    void rejectsDuplicateEmail() throws Exception {
        mvc.perform(register("Reg One", "dupe", "reg@demo.test", "changeme123"))
                .andExpect(status().isCreated());
        mvc.perform(register("Reg Two", "dupe2", "reg@demo.test", "changeme123"))
                .andExpect(status().isConflict());
    }

    @Test
    void rejectsDuplicateHandle() throws Exception {
        mvc.perform(register("Reg One", "dupe", "reg@demo.test", "changeme123"))
                .andExpect(status().isCreated());
        mvc.perform(register("Reg Two", "dupe", "reg2@demo.test", "changeme123"))
                .andExpect(status().isConflict());
    }

    @Test
    void rejectsAHandleContainingASpace() throws Exception {
        mvc.perform(register("X", "has space", "reg@demo.test", "changeme123"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsAnUppercaseHandle() throws Exception {
        mvc.perform(register("X", "Upper", "reg@demo.test", "changeme123"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsAnEmptyHandle() throws Exception {
        mvc.perform(register("X", "", "reg@demo.test", "changeme123"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsAHandleLongerThanThirtyCharacters() throws Exception {
        mvc.perform(register("X", "x".repeat(31), "reg@demo.test", "changeme123"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsAPasswordShorterThanTheMinimum() throws Exception {
        mvc.perform(register("X", "okhandle", "reg@demo.test", "short"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsMismatchedPasswordConfirmation() throws Exception {
        mvc.perform(register("X", "mismatch", "reg@demo.test", "changeme123", "somethingelse"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Passwords don't match"));
    }

    @Test
    void rejectsAMalformedEmail() throws Exception {
        mvc.perform(register("X", "bademail", "not-an-email", "changeme123"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void acceptsAOneCharacterHandle() throws Exception {
        mvc.perform(register("Tiny", "q", "tiny@demo.test", "changeme123"))
                .andExpect(status().isCreated());
    }

    @Test
    void aOneCharacterHandlePersistsSoASecondAccountCannotTakeIt() throws Exception {
        mvc.perform(register("Tiny", "q", "tiny@demo.test", "changeme123"))
                .andExpect(status().isCreated());
        mvc.perform(register("Tiny Two", "q", "tiny2@demo.test", "changeme123"))
                .andExpect(status().isConflict());
    }
}
