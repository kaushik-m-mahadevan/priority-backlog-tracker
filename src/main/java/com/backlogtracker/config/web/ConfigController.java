package com.backlogtracker.config.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.backlogtracker.config.domain.AppConfig;
import com.backlogtracker.config.service.ConfigService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/config")
@RequiredArgsConstructor
public class ConfigController {

    private final ConfigService configService;

    /** Returns the current application configuration (weights, thresholds, list values). */
    @GetMapping
    public AppConfig getConfig() {
        return configService.getConfig();
    }
}
