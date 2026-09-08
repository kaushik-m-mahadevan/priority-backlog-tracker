package com.backlogtracker.config.web;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.backlogtracker.config.domain.AppConfig;
import com.backlogtracker.config.domain.ConfigHistory;
import com.backlogtracker.config.dto.UpdateConfigRequest;
import com.backlogtracker.config.service.ConfigService;
import com.backlogtracker.security.AuthUser;
import com.backlogtracker.security.RequiresOwner;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/config")
@RequiredArgsConstructor
public class ConfigController {

    private final ConfigService configService;

    /** Current ranking configuration. Readable by anyone signed in. */
    @GetMapping
    public AppConfig getConfig() {
        return configService.getConfig();
    }

    @PutMapping
    @RequiresOwner
    public AppConfig update(@Valid @RequestBody UpdateConfigRequest request,
                            @AuthenticationPrincipal AuthUser actor) {
        return configService.update(request, actor == null ? null : actor.id());
    }

    @GetMapping("/history")
    @RequiresOwner
    public List<ConfigHistory> history() {
        return configService.history();
    }

    @PostMapping("/categories")
    @RequiresOwner
    public AppConfig addCategory(@RequestBody NameRequest body) {
        return configService.addCategory(body.name());
    }

    @DeleteMapping("/categories/{name}")
    @RequiresOwner
    public AppConfig removeCategory(@org.springframework.web.bind.annotation.PathVariable String name,
                                   @RequestParam(required = false) String reassignTo,
                                   @AuthenticationPrincipal AuthUser actor) {
        return configService.removeCategory(name, reassignTo, actor == null ? null : actor.id());
    }

    @PostMapping("/priorities")
    @RequiresOwner
    public AppConfig addPriority(@RequestBody PriorityRequest body) {
        return configService.addPriority(body.name(), body.value());
    }

    @DeleteMapping("/priorities/{name}")
    @RequiresOwner
    public AppConfig removePriority(@org.springframework.web.bind.annotation.PathVariable String name,
                                   @RequestParam(required = false) String reassignTo,
                                   @AuthenticationPrincipal AuthUser actor) {
        return configService.removePriority(name, reassignTo, actor == null ? null : actor.id());
    }

    public record NameRequest(String name) {
    }

    public record PriorityRequest(String name, Integer value) {
    }
}
