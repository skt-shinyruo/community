package com.nowcoder.community.common.kafka.trace;

import com.nowcoder.community.common.trace.TraceContextSnapshot;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.concurrent.CompletableFuture;

public final class TraceKafkaSender {

    private TraceKafkaSender() {
    }

    public static <K, V> CompletableFuture<SendResult<K, V>> send(
            KafkaTemplate<K, V> kafkaTemplate,
            String topic,
            K key,
            V value
    ) {
        ProducerRecord<K, V> record = new ProducerRecord<>(topic, key, value);
        TraceKafkaHeaders.inject(record.headers(), TraceContextSnapshot.currentOrNew());
        return kafkaTemplate.send(record);
    }
}
