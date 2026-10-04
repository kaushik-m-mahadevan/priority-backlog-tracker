package com.backlogtracker.commons.security;

import java.io.IOException;
import java.util.List;

import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.backlogtracker.commons.session.service.SessionService;
import com.backlogtracker.commons.user.domain.User;
import com.backlogtracker.commons.user.repository.UserRepository;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/**
 * Reads {@code Authorization: Bearer <jwt>}, verifies it, then loads the user from the
 * database and populates the security context with a fresh {@link AuthUser} (current
 * role + status). A token whose user no longer exists, or whose tracked session has been
 * closed (sign-out) or has gone quiet past the inactivity timeout, is left unauthenticated
 * → 401. Every request that gets through counts as activity for the session.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String PREFIX = "Bearer ";

    /** Request attribute carrying the verified token's session id to controllers. */
    public static final String SESSION_ID_ATTRIBUTE = "pbt.sessionId";

    /** Validated against the session but not counted as activity, so the server can tell
     *  an idle-timer logout from a deliberate one (see {@code SessionService.end}). */
    private static final String LOGOUT_PATH = "/api/auth/logout";

    private final JwtService jwtService;
    private final UserRepository users;
    private final SessionService sessions;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain)
            throws ServletException, IOException {

        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(PREFIX)
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                var claims = jwtService.parse(header.substring(PREFIX.length()));
                User user = users.findById(claims.userId()).orElse(null);
                boolean countsAsActivity = !LOGOUT_PATH.equals(request.getRequestURI());
                if (user != null && sessions.authorize(claims.sessionId(), claims.userId(), countsAsActivity)) {
                    request.setAttribute(SESSION_ID_ATTRIBUTE, claims.sessionId());
                    AuthUser principal = AuthUser.from(user);
                    var authorities =
                            List.of(new SimpleGrantedAuthority(principal.role().authority()));
                    var auth = new UsernamePasswordAuthenticationToken(principal, null, authorities);
                    auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            } catch (JwtException ignored) {
                // leave unauthenticated
            }
        }
        chain.doFilter(request, response);
    }
}
