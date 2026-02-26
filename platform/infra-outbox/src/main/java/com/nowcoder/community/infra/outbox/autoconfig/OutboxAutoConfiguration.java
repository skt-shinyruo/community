package com.nowcoder.community.infra.outbox.autoconfig;

import com.nowcoder.community.infra.outbox.OutboxEventMapper;
import com.nowcoder.community.infra.outbox.OutboxEventService;
import com.nowcoder.community.infra.outbox.OutboxProperties;
import com.nowcoder.community.infra.outbox.OutboxRelayJob;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.KafkaTemplate;

@AutoConfiguration
@EnableConfigurationProperties(OutboxProperties.class)
public class OutboxAutoConfiguration {

    @Bean
    @ConditionalOnBean(OutboxEventMapper.class)
    @ConditionalOnMissingBean
    public OutboxEventService outboxEventService(OutboxEventMapper outboxEventMapper, OutboxProperties properties) {
        return new OutboxEventService(outboxEventMapper, properties);
    }

    @Bean
    @ConditionalOnProperty(name = "events.outbox.enabled", havingValue = "true")
    @ConditionalOnBean({OutboxEventService.class, KafkaTemplate.class})
    @ConditionalOnMissingBean
    public OutboxRelayJob outboxRelayJob(
            OutboxEventService outboxEventService,
            KafkaTemplate<String, String> kafkaTemplate,
            OutboxProperties properties,
            MeterRegistry meterRegistry,
            @Value("${spring.application.name:unknown}") String appName
    ) {
        return new OutboxRelayJob(outboxEventService, kafkaTemplate, properties, meterRegistry, appName);
    }
}
