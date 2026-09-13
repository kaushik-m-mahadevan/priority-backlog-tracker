package com.backlogtracker.backlogtracker.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.backlogtracker.backlogtracker.config.domain.AppConfig;
import com.backlogtracker.backlogtracker.config.repository.ConfigRepository;
import com.backlogtracker.backlogtracker.config.service.ConfigService;

@SpringBootTest
class ConfigSeedingTest {

    @Autowired
    ConfigRepository repository;

    @Autowired
    ConfigService configService;

    @Test
    void seedsSingletonConfigOnStartup() {
        assertThat(repository.findAll()).hasSize(1);

        AppConfig config = repository.findById(AppConfig.SINGLETON_ID).orElseThrow();
        assertThat(config.getPriorities())
                .containsExactly("Critical", "High", "Medium", "Low");
        assertThat(config.getPriorityValues())
                .containsEntry("Critical", 4)
                .containsEntry("Low", 1);
        assertThat(config.getBuriedPriorityLevels()).containsExactly("Low");
        assertThat(config.getUrgencyWindowDays()).isEqualTo(14);
        assertThat(config.getStaleThresholdDays()).isEqualTo(14);
        assertThat(config.getBuriedThresholdDays()).isEqualTo(30);
        assertThat(config.getDefaultDueDateOffsetDays()).isEqualTo(30);
        assertThat(config.getEffortCapDays()).isEqualTo(30);
    }

    @Test
    void defaultWeightsSumToExactlyOne() {
        AppConfig config = configService.getConfig();
        double sum = config.getPriorityWeight()
                + config.getUrgencyWeight()
                + config.getEffortWeight();
        assertThat(sum).isCloseTo(1.0, within(1e-9));
    }

    @Test
    void getConfigIsIdempotentAndDoesNotDuplicate() {
        configService.getConfig();
        configService.getConfig();
        assertThat(repository.count()).isEqualTo(1);
    }
}
