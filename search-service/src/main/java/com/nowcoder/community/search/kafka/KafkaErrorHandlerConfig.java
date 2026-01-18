package com.nowcoder.community.search.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.common.event.EventTopics;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

import java.time.Instant;

@Configuration
public class KafkaErrorHandlerConfig {

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        ConsumerRecordRecoverer recoverer = (ConsumerRecord<?, ?> record, Exception ex) -> {
            KafkaDlqRecord dlq = new KafkaDlqRecord();
            dlq.setOriginalTopic(record.topic());
            dlq.setOriginalPartition(record.partition());
            dlq.setOriginalOffset(record.offset());
            dlq.setKey(record.key() == null ? null : record.key().toString());
            dlq.setPayload(record.value() == null ? null : record.value().toString());
            dlq.setErrorType(ex.getClass().getName());
            dlq.setErrorMessage(ex.getMessage());
            dlq.setFailedAt(Instant.now());

            try {
                String json = objectMapper.writeValueAsString(dlq);
                kafkaTemplate.send(record.topic() + EventTopics.DLQ_SUFFIX, dlq.getKey(), json);
            } catch (Exception ignore) {
                kafkaTemplate.send(record.topic() + EventTopics.DLQ_SUFFIX, dlq.getKey(), dlq.getPayload());
            }
        };
        return new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 3L));
    }
}

