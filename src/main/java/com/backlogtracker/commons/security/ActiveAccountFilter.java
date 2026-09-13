package com.backlogtracker.commons.security;

import java.io.IOException;
import java.util.Set;

import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Blocks a {@code PENDING} account from every {@code /api/**} endpoint except the ones it
 * needs to render the "awaiting approval" screen. Returns 403 with a JSON body the SPA
 * recognises. Registered right after {@link JwtAuthenticationFilter} in SecurityConfig.
 */
@Component
class ActiveAccountFilter extends OncePerRequestFilter {

    private static final Set<String> ALLOWED = Set.of("/api/auth/me", "/api/auth/logout");

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain)
            throws ServletException, IOException {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthUser user && !user.isActive()) {
            String path = request.getRequestURI();
            if (path.startsWith("/api/") && !ALLOWED.contains(path)) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.setContentType("application/json");
                response.getWriter().write(
                        "{\"status\":\"PENDING\",\"message\":\"Your account is awaiting approval.\"}");
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
