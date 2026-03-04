package com.nowcoder.community.infra.kafka.autoconfig;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.infra.kafka.dlq.KafkaDlqPublisher;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * Kafka DLQ 发布器自动装配：
 * - 仅当 spring-kafka 在 classpath 且存在 KafkaTemplate 时启用
 */
@AutoConfiguration
@ConditionalOnClass(KafkaTemplate.class)
public class KafkaInfraAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public KafkaDlqPublisher kafkaDlqPublisher(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        return new KafkaDlqPublisher(kafkaTemplate, objectMapper);
    }
}
