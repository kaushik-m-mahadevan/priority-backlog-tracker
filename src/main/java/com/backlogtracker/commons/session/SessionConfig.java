package com.backlogtracker.commons.session;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Turns on {@code @Scheduled} for the inactivity sweep in {@code SessionService}. */
@Configuration
@EnableScheduling
public class SessionConfig {
}
