package com.backlogtracker.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.backlogtracker.config.service.ConfigService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Creates the singleton {@link com.backlogtracker.config.domain.AppConfig} document on
 * first startup. Runs early so later seeders (users, etc.) can depend on config existing.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
@Slf4j
public class ConfigSeeder implements ApplicationRunner {

    private final ConfigService configService;

    @Override
    public void run(ApplicationArguments args) {
        configService.seedIfAbsent();
        log.info("App config ready (id={})",
                com.backlogtracker.config.domain.AppConfig.SINGLETON_ID);
    }
}
