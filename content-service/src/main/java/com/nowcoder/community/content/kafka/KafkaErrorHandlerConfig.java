package com.nowcoder.community.content.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.platform.kafka.dlq.KafkaDlqPublisher;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaErrorHandlerConfig {

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper, MeterRegistry meterRegistry) {
        KafkaDlqPublisher dlqPublisher = new KafkaDlqPublisher(kafkaTemplate, objectMapper);
        ConsumerRecordRecoverer recoverer = (ConsumerRecord<?, ?> record, Exception ex) -> {
            dlqPublisher.publish(record, ex);
            meterRegistry.counter(
                    "kafka_dlq_published_total",
                    Tags.of(
                            "original_topic", record.topic(),
                            "error_type", ex.getClass().getSimpleName()
                    )
            ).increment();
        };

        // 与 message/search 对齐：3 次重试，仍失败进入 DLQ（避免卡死消费组）
        return new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 3L));
    }
}
