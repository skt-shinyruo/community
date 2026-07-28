package com.nowcoder.community.common.outbox.autoconfig;

import com.nowcoder.community.common.outbox.OutboxWorkerScheduler;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class OutboxAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(OutboxAutoConfiguration.class))
            .withBean(JdbcTemplate.class, () -> mock(JdbcTemplate.class));

    @Test
    void enabledOutboxShouldCreateWorkerByDefault() {
        contextRunner
                .withPropertyValues("events.outbox.enabled=true")
                .run(context -> assertThat(context).hasSingleBean(OutboxWorkerScheduler.class));
    }

    @Test
    void disabledWorkerShouldKeepOutboxStoreWithoutCreatingScheduler() {
        contextRunner
                .withPropertyValues(
                        "events.outbox.enabled=true",
                        "events.outbox.worker-enabled=false"
                )
                .run(context -> {
                    assertThat(context).hasBean("outboxEventStore");
                    assertThat(context).doesNotHaveBean(OutboxWorkerScheduler.class);
                });
    }
}
