package com.backlogtracker.config.service;

import org.springframework.stereotype.Service;

import com.backlogtracker.config.domain.AppConfig;
import com.backlogtracker.config.repository.ConfigRepository;

import lombok.RequiredArgsConstructor;

/**
 * Read/seed access to the single {@link AppConfig} document. Weight validation and the
 * dynamic category/priority list-editing safety rules (docs/design.md §7) arrive with the
 * Settings screen in a later step.
 */
@Service
@RequiredArgsConstructor
public class ConfigService {

    private final ConfigRepository repository;

    /** Returns the singleton config, creating it from defaults if it is missing. */
    public AppConfig getConfig() {
        return repository.findById(AppConfig.SINGLETON_ID)
                .orElseGet(() -> repository.save(AppConfig.defaults()));
    }

    /**
     * Ensures the singleton config document exists. Called once on startup so the rest of
     * the app can assume it is always present. Existing config is never overwritten.
     */
    public AppConfig seedIfAbsent() {
        return getConfig();
    }
}
