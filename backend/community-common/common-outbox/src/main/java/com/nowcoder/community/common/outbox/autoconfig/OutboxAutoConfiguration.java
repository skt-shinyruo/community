package com.nowcoder.community.common.outbox.autoconfig;

import com.nowcoder.community.common.outbox.JdbcOutboxEventStore;
import com.nowcoder.community.common.outbox.OutboxHandler;
import com.nowcoder.community.common.outbox.OutboxProperties;
import com.nowcoder.community.common.outbox.OutboxWorkerScheduler;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Clock;

@AutoConfiguration
@EnableConfigurationProperties(OutboxProperties.class)
public class OutboxAutoConfiguration {

    @Bean
    @ConditionalOnClass(JdbcTemplate.class)
    @ConditionalOnMissingBean
    public JdbcOutboxEventStore outboxEventStore(ObjectProvider<JdbcTemplate> jdbcTemplateProvider) {
        JdbcTemplate jdbcTemplate = jdbcTemplateProvider == null ? null : jdbcTemplateProvider.getIfAvailable();
        if (jdbcTemplate == null) {
            throw new IllegalStateException("events.outbox requires JdbcTemplate");
        }
        return new JdbcOutboxEventStore(jdbcTemplate);
    }

    @Bean
    @ConditionalOnProperty(prefix = "events.outbox", name = "enabled", havingValue = "true")
    @ConditionalOnMissingBean
    public Clock outboxClock() {
        return Clock.systemUTC();
    }

    @Bean
    @ConditionalOnProperty(prefix = "events.outbox", name = "enabled", havingValue = "true")
    @ConditionalOnMissingBean
    public OutboxWorkerScheduler outboxWorkerScheduler(
            JdbcOutboxEventStore store,
            ObjectProvider<java.util.List<OutboxHandler>> handlersProvider,
            OutboxProperties properties,
            Clock clock
    ) {
        return new OutboxWorkerScheduler(store, handlersProvider, properties, clock);
    }
}
