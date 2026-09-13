package com.backlogtracker.commons;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * A single injectable {@link Clock} so all date math (due-date defaults, urgency, aging)
 * is deterministic in tests. Stored times are UTC (design §22).
 */
@Configuration
public class ClockConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
