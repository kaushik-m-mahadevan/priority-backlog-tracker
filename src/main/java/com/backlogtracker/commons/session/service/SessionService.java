package com.backlogtracker.commons.session.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.backlogtracker.commons.group.domain.PlatformConfig;
import com.backlogtracker.commons.group.repository.PlatformConfigRepository;
import com.backlogtracker.commons.security.JwtProperties;
import com.backlogtracker.commons.session.domain.LogoutReason;
import com.backlogtracker.commons.session.domain.UserSession;
import com.backlogtracker.commons.session.repository.UserSessionRepository;
import com.backlogtracker.commons.user.domain.User;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Login/logout tracking plus inactivity expiry. A session opens when a token is issued
 * ({@code JwtService.issue}) and closes on sign-out or after {@link #timeout()} without
 * activity. Activity = any authenticated request (see {@code JwtAuthenticationFilter}),
 * plus the browser's own heartbeat while the user is interacting without hitting the API.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SessionService {

    /** 0 means sessions never time out for inactivity (the default); an admin can set one. */
    public static final int DEFAULT_TIMEOUT_MINUTES = 0;
    public static final int MAX_TIMEOUT_MINUTES = 1440;

    /** Don't write on every request: a page load fires a burst of them. */
    static final Duration TOUCH_THROTTLE = Duration.ofSeconds(30);

    /** The browser heartbeats at most about once a minute, so its idle timer can fire this
     *  much before the server's own last-activity + timeout and still be honest. */
    static final Duration IDLE_CLAIM_SLACK = Duration.ofMinutes(2);

    private static final Duration CONFIG_CACHE_TTL = Duration.ofSeconds(30);

    private record CachedTimeout(Duration value, Instant loadedAt) {
    }

    private final UserSessionRepository sessions;
    private final PlatformConfigRepository platformConfig;
    private final JwtProperties jwtProperties;
    private final MongoOperations mongo;
    private final Clock clock;
    private final AtomicReference<CachedTimeout> cachedTimeout = new AtomicReference<>();

    public UserSession start(User user) {
        Instant now = Instant.now(clock);
        return sessions.save(UserSession.builder()
                .userId(user.getId())
                .userName(user.getName())
                .userEmail(user.getEmail())
                .loginAt(now)
                .lastActivityAt(now)
                .build());
    }

    /**
     * Whether this token's session may make a request right now. A session that has already
     * been closed, belongs to someone else, or has gone quiet past the timeout is rejected
     * (and closed, if it was merely stale). {@code countsAsActivity=false} validates without
     * refreshing the clock, used for sign-out itself so an idle-timer logout can still be told
     * apart from an explicit one.
     */
    public boolean authorize(String sessionId, String userId, boolean countsAsActivity) {
        UserSession s = sessionId == null ? null : sessions.findById(sessionId).orElse(null);
        if (s == null || s.getLogoutAt() != null || !s.getUserId().equals(userId)) {
            return false;
        }
        Instant now = Instant.now(clock);
        if (timeoutEnabled() && Duration.between(s.getLastActivityAt(), now).compareTo(timeout()) > 0) {
            closeIfOpen(s.getId(), s.getLastActivityAt().plus(timeout()), LogoutReason.INACTIVITY_TIMEOUT);
            return false;
        }
        if (countsAsActivity && Duration.between(s.getLastActivityAt(), now).compareTo(TOUCH_THROTTLE) >= 0) {
            mongo.updateFirst(openSession(s.getId()), new Update().set("lastActivityAt", now), UserSession.class);
        }
        return true;
    }

    /**
     * Ends the session. {@code reportedInactive} is the browser saying its idle timer fired;
     * it is honored only if the server's own record is close to the timeout too, so it can't
     * relabel an ordinary sign-out. Idempotent: an already-closed session is left as it was.
     */
    public void end(String sessionId, boolean reportedInactive) {
        UserSession s = sessions.findById(sessionId).orElse(null);
        if (s == null || s.getLogoutAt() != null) {
            return;
        }
        Instant now = Instant.now(clock);
        Duration quiet = Duration.between(s.getLastActivityAt(), now);
        if (reportedInactive && timeoutEnabled() && quiet.plus(IDLE_CLAIM_SLACK).compareTo(timeout()) >= 0) {
            Instant effective = s.getLastActivityAt().plus(timeout());
            closeIfOpen(sessionId, effective.isAfter(now) ? now : effective, LogoutReason.INACTIVITY_TIMEOUT);
        } else {
            closeIfOpen(sessionId, now, LogoutReason.SIGN_OUT);
        }
    }

    /** Closes sessions whose user closed the tab or walked away and so never made another
     *  request to trip {@link #authorize}; without this their logout time would stay empty.
     *  With the inactivity timeout off, the only thing that still ends an abandoned session is
     *  its token expiring, so those are closed (as TOKEN_EXPIRED) instead. */
    @Scheduled(fixedDelayString = "${app.session.sweep-interval-ms:60000}")
    public void closeExpired() {
        Instant now = Instant.now(clock);
        if (!timeoutEnabled()) {
            Duration tokenLife = Duration.ofMinutes(jwtProperties.expirationMinutes());
            List<UserSession> lapsed = sessions.findByLogoutAtIsNullAndLoginAtBefore(now.minus(tokenLife));
            for (UserSession s : lapsed) {
                closeIfOpen(s.getId(), s.getLastActivityAt(), LogoutReason.TOKEN_EXPIRED);
            }
            if (!lapsed.isEmpty()) {
                log.info("Closed {} session(s) whose token expired", lapsed.size());
            }
            return;
        }
        Duration timeout = timeout();
        List<UserSession> stale = sessions.findByLogoutAtIsNullAndLastActivityAtBefore(now.minus(timeout));
        for (UserSession s : stale) {
            closeIfOpen(s.getId(), s.getLastActivityAt().plus(timeout), LogoutReason.INACTIVITY_TIMEOUT);
        }
        if (!stale.isEmpty()) {
            log.info("Closed {} session(s) after {} of inactivity", stale.size(), timeout);
        }
    }

    public List<UserSession> recent(String userId, int limit) {
        PageRequest page = PageRequest.of(0, Math.max(1, Math.min(limit, 500)));
        return userId == null || userId.isBlank()
                ? sessions.findAllByOrderByLoginAtDesc(page)
                : sessions.findByUserIdOrderByLoginAtDesc(userId, page);
    }

    public boolean timeoutEnabled() {
        return !timeout().isZero();
    }

    /** {@link Duration#ZERO} when the inactivity timeout is off. */
    public Duration timeout() {
        Instant now = Instant.now(clock);
        CachedTimeout cached = cachedTimeout.get();
        if (cached != null && Duration.between(cached.loadedAt(), now).compareTo(CONFIG_CACHE_TTL) < 0) {
            return cached.value();
        }
        Integer minutes = platformConfig.findById(PlatformConfig.SINGLETON_ID)
                .map(PlatformConfig::getSessionTimeoutMinutes).orElse(null);
        Duration value = Duration.ofMinutes(minutes == null || minutes < 0 ? DEFAULT_TIMEOUT_MINUTES : minutes);
        cachedTimeout.set(new CachedTimeout(value, now));
        return value;
    }

    public void setTimeoutMinutes(int minutes) {
        if (minutes < 0 || minutes > MAX_TIMEOUT_MINUTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Session timeout must be 0 (never) or 1 to " + MAX_TIMEOUT_MINUTES + " minutes");
        }
        PlatformConfig cfg = platformConfig.findById(PlatformConfig.SINGLETON_ID)
                .orElseGet(PlatformConfig::defaults);
        cfg.setSessionTimeoutMinutes(minutes);
        platformConfig.save(cfg);
        cachedTimeout.set(null);
    }

    private static Query openSession(String id) {
        return Query.query(Criteria.where("_id").is(id).and("logoutAt").is(null));
    }

    /** Conditional on still-open, so a concurrent sign-out / sweep / request can't overwrite
     *  whichever close landed first. */
    private void closeIfOpen(String id, Instant at, LogoutReason reason) {
        mongo.updateFirst(openSession(id),
                new Update().set("logoutAt", at).set("logoutReason", reason), UserSession.class);
    }
}
