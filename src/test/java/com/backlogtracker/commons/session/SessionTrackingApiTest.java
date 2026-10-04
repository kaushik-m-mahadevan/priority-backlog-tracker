package com.backlogtracker.commons.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import com.backlogtracker.commons.security.JwtService;
import com.backlogtracker.commons.session.domain.LogoutReason;
import com.backlogtracker.commons.session.domain.UserSession;
import com.backlogtracker.commons.session.repository.UserSessionRepository;
import com.backlogtracker.commons.session.service.SessionService;
import com.backlogtracker.commons.user.domain.AccountStatus;
import com.backlogtracker.commons.user.domain.Role;
import com.backlogtracker.commons.user.domain.User;
import com.backlogtracker.commons.user.repository.UserRepository;

@SpringBootTest
@AutoConfigureMockMvc
class SessionTrackingApiTest {

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired UserSessionRepository sessions;
    @Autowired SessionService sessionService;
    @Autowired JwtService jwt;
    @Autowired PasswordEncoder encoder;

    private User member;
    private User admin;

    @BeforeEach
    void setUp() {
        sessionService.setTimeoutMinutes(10);
        member = users.save(User.builder().name("Sess Member").email("sess-member@x.test")
                .passwordHash(encoder.encode("pw-12345")).role(Role.USER).status(AccountStatus.ACTIVE)
                .handle("sessmember").build());
        admin = users.save(User.builder().name("Sess Admin").email("sess-admin@x.test")
                .passwordHash("x").role(Role.ADMIN).status(AccountStatus.ACTIVE).handle("sessadmin").build());
    }

    @AfterEach
    void tearDown() {
        sessionService.setTimeoutMinutes(SessionService.DEFAULT_TIMEOUT_MINUTES);
        for (User u : List.of(member, admin)) {
            sessions.findByUserIdOrderByLoginAtDesc(u.getId(), org.springframework.data.domain.Pageable.unpaged())
                    .forEach(sessions::delete);
            users.deleteById(u.getId());
        }
    }

    private UserSession onlySessionOf(User u) {
        List<UserSession> all = sessions.findByUserIdOrderByLoginAtDesc(u.getId(),
                org.springframework.data.domain.Pageable.unpaged());
        assertThat(all).hasSize(1);
        return all.get(0);
    }

    private void backdateActivity(UserSession s, Duration ago) {
        s.setLastActivityAt(Instant.now().minus(ago));
        sessions.save(s);
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    @Test
    void loggingInOpensASessionWithTheUserAndLoginTime() throws Exception {
        Instant before = Instant.now();
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"sess-member@x.test\",\"password\":\"pw-12345\"}"))
                .andExpect(status().isOk());

        UserSession s = onlySessionOf(member);
        assertThat(s.getUserEmail()).isEqualTo("sess-member@x.test");
        assertThat(s.getUserName()).isEqualTo("Sess Member");
        assertThat(s.getLoginAt()).isAfterOrEqualTo(before.minusSeconds(1));
        assertThat(s.getLogoutAt()).isNull();
    }

    @Test
    void signingOutClosesTheSessionAndKillsItsToken() throws Exception {
        String token = jwt.issue(member);
        mvc.perform(get("/api/auth/me").header("Authorization", bearer(token))).andExpect(status().isOk());

        mvc.perform(post("/api/auth/logout").header("Authorization", bearer(token)))
                .andExpect(status().isNoContent());

        UserSession s = onlySessionOf(member);
        assertThat(s.getLogoutAt()).isNotNull();
        assertThat(s.getLogoutReason()).isEqualTo(LogoutReason.SIGN_OUT);
        mvc.perform(get("/api/auth/me").header("Authorization", bearer(token))).andExpect(status().isUnauthorized());
    }

    @Test
    void aSessionQuietPastTheTimeoutIsClosedAtLastActivityPlusTimeoutAndRejected() throws Exception {
        String token = jwt.issue(member);
        UserSession s = onlySessionOf(member);
        backdateActivity(s, Duration.ofMinutes(25));
        Instant lastActivity = sessions.findById(s.getId()).orElseThrow().getLastActivityAt();

        mvc.perform(get("/api/auth/me").header("Authorization", bearer(token))).andExpect(status().isUnauthorized());

        UserSession closed = sessions.findById(s.getId()).orElseThrow();
        assertThat(closed.getLogoutReason()).isEqualTo(LogoutReason.INACTIVITY_TIMEOUT);
        assertThat(closed.getLogoutAt()).isEqualTo(lastActivity.plus(Duration.ofMinutes(10)));
    }

    @Test
    void activityKeepsASessionAliveAndMovesItsLastActivityForward() throws Exception {
        String token = jwt.issue(member);
        UserSession s = onlySessionOf(member);
        backdateActivity(s, Duration.ofMinutes(9));
        Instant stale = sessions.findById(s.getId()).orElseThrow().getLastActivityAt();

        mvc.perform(post("/api/auth/heartbeat").header("Authorization", bearer(token)))
                .andExpect(status().isNoContent());

        UserSession after = sessions.findById(s.getId()).orElseThrow();
        assertThat(after.getLogoutAt()).isNull();
        assertThat(after.getLastActivityAt()).isAfter(stale.plus(Duration.ofMinutes(5)));
    }

    @Test
    void theSweepClosesIdleSessionsThatNeverMadeAnotherRequest() {
        jwt.issue(member);
        UserSession s = onlySessionOf(member);
        backdateActivity(s, Duration.ofMinutes(30));
        Instant lastActivity = sessions.findById(s.getId()).orElseThrow().getLastActivityAt();

        sessionService.closeExpired();

        UserSession closed = sessions.findById(s.getId()).orElseThrow();
        assertThat(closed.getLogoutReason()).isEqualTo(LogoutReason.INACTIVITY_TIMEOUT);
        assertThat(closed.getLogoutAt()).isEqualTo(lastActivity.plus(Duration.ofMinutes(10)));
    }

    @Test
    void theSweepLeavesRecentlyActiveSessionsOpen() {
        jwt.issue(member);
        UserSession s = onlySessionOf(member);
        backdateActivity(s, Duration.ofMinutes(3));

        sessionService.closeExpired();

        assertThat(sessions.findById(s.getId()).orElseThrow().getLogoutAt()).isNull();
    }

    @Test
    void anIdleTimerLogoutIsRecordedAsInactivityOnlyWhenTheServerAgreesItWasIdle() throws Exception {
        String idleToken = jwt.issue(member);
        UserSession idle = onlySessionOf(member);
        backdateActivity(idle, Duration.ofMinutes(9)); // within the 2-minute heartbeat slack of the timeout

        mvc.perform(post("/api/auth/logout").param("inactive", "true").header("Authorization", bearer(idleToken)))
                .andExpect(status().isNoContent());
        assertThat(sessions.findById(idle.getId()).orElseThrow().getLogoutReason())
                .isEqualTo(LogoutReason.INACTIVITY_TIMEOUT);

        String activeToken = jwt.issue(member); // fresh: the "idle" claim is not believable
        mvc.perform(post("/api/auth/logout").param("inactive", "true").header("Authorization", bearer(activeToken)))
                .andExpect(status().isNoContent());
        UserSession active = sessions.findByUserIdOrderByLoginAtDesc(member.getId(),
                org.springframework.data.domain.Pageable.unpaged()).get(0);
        assertThat(active.getLogoutReason()).isEqualTo(LogoutReason.SIGN_OUT);
    }

    @Test
    void aTokenIssuedWithoutASessionClaimIsRejected() throws Exception {
        String legacy = io.jsonwebtoken.Jwts.builder().subject(member.getId())
                .expiration(java.util.Date.from(Instant.now().plusSeconds(600)))
                .signWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(
                        com.backlogtracker.commons.crypto.Sha256.digest("test123")))
                .compact();

        mvc.perform(get("/api/auth/me").header("Authorization", bearer(legacy))).andExpect(status().isUnauthorized());
    }

    @Test
    void aTokenCannotBeUsedAgainstAnotherUsersSession() throws Exception {
        String memberToken = jwt.issue(member);
        UserSession memberSession = onlySessionOf(member);
        memberSession.setUserId(admin.getId());
        sessions.save(memberSession);

        mvc.perform(get("/api/auth/me").header("Authorization", bearer(memberToken)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminsCanSeeSessionsAndChangeTheTimeoutButMembersCannot() throws Exception {
        String adminToken = jwt.issue(admin);
        String memberToken = jwt.issue(member);

        mvc.perform(get("/api/admin/sessions").param("userId", member.getId())
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].userEmail").value("sess-member@x.test"))
                .andExpect(jsonPath("$[0].active").value(true));
        mvc.perform(get("/api/admin/sessions").header("Authorization", bearer(memberToken)))
                .andExpect(status().isForbidden());

        mvc.perform(put("/api/admin/session-policy").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"timeoutMinutes\":30}").header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.timeoutMinutes").value(30));
        mvc.perform(get("/api/auth/session-policy").header("Authorization", bearer(memberToken)))
                .andExpect(jsonPath("$.timeoutMinutes").value(30));
        mvc.perform(put("/api/admin/session-policy").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"timeoutMinutes\":0}").header("Authorization", bearer(adminToken)))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/admin/session-policy").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"timeoutMinutes\":30}").header("Authorization", bearer(memberToken)))
                .andExpect(status().isForbidden());
    }
}
