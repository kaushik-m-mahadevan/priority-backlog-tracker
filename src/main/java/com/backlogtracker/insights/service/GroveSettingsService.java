package com.backlogtracker.insights.service;

import org.springframework.stereotype.Service;

import com.backlogtracker.insights.domain.GroveSettings;
import com.backlogtracker.insights.repository.GroveSettingsRepository;

import lombok.RequiredArgsConstructor;

/** Per-user opt-in for the grove animation — see {@link GroveSettings} for why this isn't
 *  just a field on {@code User}. */
@Service
@RequiredArgsConstructor
public class GroveSettingsService {

    private final GroveSettingsRepository repository;

    public boolean animationsEnabled(String userId) {
        return repository.findById(userId).map(GroveSettings::isAnimationsEnabled).orElse(false);
    }

    public boolean setAnimationsEnabled(String userId, boolean enabled) {
        GroveSettings s = repository.findById(userId)
                .orElseGet(() -> GroveSettings.builder().userId(userId).build());
        s.setAnimationsEnabled(enabled);
        repository.save(s);
        return enabled;
    }
}
