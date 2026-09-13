package com.backlogtracker.commons.group.domain;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Platform-wide (commons) config — deliberately separate from any one applet's own
 * settings (design: platform integration). Currently holds only the per-applet group
 * cap, previously a field on Backlog Tracker's own {@code AppConfig} ranking-formula
 * singleton — a platform-wide concept doesn't belong inside one applet's config.
 */
@Document("platformConfig")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlatformConfig {

    public static final String SINGLETON_ID = "platform-config";

    @Id
    private String id;

    /** Applet key -&gt; max groups a user may belong to for that applet. Missing/0 = unlimited. */
    private Map<String, Integer> maxGroupsPerApplet;

    public static PlatformConfig defaults() {
        Map<String, Integer> caps = new LinkedHashMap<>();
        caps.put(Group.APPLET_BACKLOG_TRACKER, 5);
        return PlatformConfig.builder().id(SINGLETON_ID).maxGroupsPerApplet(caps).build();
    }
}
