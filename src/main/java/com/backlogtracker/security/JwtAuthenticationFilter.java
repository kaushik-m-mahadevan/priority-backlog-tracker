package com.backlogtracker.security;

import java.io.IOException;
import java.util.List;

import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.backlogtracker.user.domain.User;
import com.backlogtracker.user.repository.UserRepository;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/**
 * Reads {@code Authorization: Bearer <jwt>}, verifies it, then loads the user from the
 * database and populates the security context with a fresh {@link AuthUser} (current
 * role + status). A token whose user no longer exists is left unauthenticated → 401.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final UserRepository users;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain)
            throws ServletException, IOException {

        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(PREFIX)
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                String userId = jwtService.parseSubject(header.substring(PREFIX.length()));
                User user = users.findById(userId).orElse(null);
                if (user != null) {
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
