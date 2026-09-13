package com.backlogtracker.commons.user;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.backlogtracker.commons.user.domain.AccountStatus;
import com.backlogtracker.commons.user.domain.Role;
import com.backlogtracker.commons.user.domain.User;
import com.backlogtracker.commons.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Local/test bootstrap: on an empty database, create a single {@code ADMIN}/{@code ACTIVE}
 * account so `--spring.profiles.active=demo`, plain local runs and the test suite have a
 * way in. Fixed, non-configurable credentials — there is no {@code SEED_USER_*} anymore.
 *
 * <p>Disabled in production (`app.local-bootstrap.enabled=false` in application-prod.yml):
 * prod's admin already exists and is promoted by {@code LegacyDataMigration}.
 */
@Component
@Order(20)
@ConditionalOnProperty(name = "app.local-bootstrap.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class UserSeeder implements ApplicationRunner {

    static final String EMAIL = "test123";
    static final String PASSWORD = "test123";

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(ApplicationArguments args) {
        if (users.count() > 0 || users.existsByEmailIgnoreCase(EMAIL)) {
            return;
        }
        users.save(User.builder()
                .name("Test User")
                .email(EMAIL)
                .passwordHash(passwordEncoder.encode(PASSWORD))
                .role(Role.ADMIN)
                .status(AccountStatus.ACTIVE)
                .handle("admin")
                .build());
        log.info("Local bootstrap: created ADMIN account '{}'", EMAIL);
    }
}
