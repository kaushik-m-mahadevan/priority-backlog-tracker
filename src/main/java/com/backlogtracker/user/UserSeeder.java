package com.backlogtracker.user;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.backlogtracker.user.domain.Role;
import com.backlogtracker.user.domain.User;
import com.backlogtracker.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Creates the placeholder dev account on first startup (design §8: everyone is an Owner
 * for now). Real founder accounts replace this when their data is provided.
 */
@Component
@Order(10)
@EnableConfigurationProperties(SeedUserProperties.class)
@RequiredArgsConstructor
@Slf4j
public class UserSeeder implements ApplicationRunner {

    private final SeedUserProperties props;
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(ApplicationArguments args) {
        if (!props.enabled()) {
            return;
        }
        if (users.existsByEmailIgnoreCase(props.email())) {
            return;
        }
        users.save(User.builder()
                .name(props.name())
                .email(props.email())
                .passwordHash(passwordEncoder.encode(props.password()))
                .role(Role.OWNER)
                .userCode(props.userCode())
                .build());
        log.info("Seeded dev user '{}' (role=OWNER, code={})", props.email(), props.userCode());
    }
}
