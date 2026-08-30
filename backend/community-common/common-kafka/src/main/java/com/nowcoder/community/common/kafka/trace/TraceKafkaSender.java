package com.nowcoder.community.common.kafka.trace;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

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
        TraceKafkaHeaders.inject(record.headers());
        return kafkaTemplate.send(record);
    }

    public static <K, V> void sendSync(
            KafkaTemplate<K, V> kafkaTemplate,
            String topic,
            K key,
            V value,
            String errorContext
    ) {
        try {
            send(kafkaTemplate, topic, key, value).join();
        } catch (CompletionException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            throw new IllegalStateException(errorContext + ": " + topic, cause);
        } catch (RuntimeException e) {
            throw new IllegalStateException(errorContext + ": " + topic, e);
        }
    }
}
