package com.nowcoder.community.common.observability.kafka;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.springframework.kafka.listener.RecordInterceptor;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

public class RuntimeKafkaRecordInterceptor implements RecordInterceptor<Object, Object> {

    private static final long LAG_CHECK_INTERVAL_NANOS = Duration.ofSeconds(30).toNanos();

    private final KafkaRuntimeLogger logger;
    private final LongSupplier nanoTime;
    private final Map<TopicPartition, Long> lastLagChecks = new ConcurrentHashMap<>();

    public RuntimeKafkaRecordInterceptor(KafkaRuntimeLogger logger) {
        this(logger, System::nanoTime);
    }

    RuntimeKafkaRecordInterceptor(KafkaRuntimeLogger logger, LongSupplier nanoTime) {
        this.logger = logger;
        this.nanoTime = nanoTime;
    }

    @Override
    public ConsumerRecord<Object, Object> intercept(ConsumerRecord<Object, Object> record, Consumer<Object, Object> consumer) {
        if (record == null || consumer == null) {
            return record;
        }
        TopicPartition partition = new TopicPartition(record.topic(), record.partition());
        if (shouldCheck(partition)) {
            logLag(record, consumer, partition);
        }
        return record;
    }

    private boolean shouldCheck(TopicPartition partition) {
        long now = nanoTime.getAsLong();
        Long lastCheck = lastLagChecks.get(partition);
        if (lastCheck != null && now - lastCheck < LAG_CHECK_INTERVAL_NANOS) {
            return false;
        }
        lastLagChecks.put(partition, now);
        return true;
    }

    private void logLag(ConsumerRecord<Object, Object> record, Consumer<Object, Object> consumer, TopicPartition partition) {
        try {
            Long endOffset = consumer.endOffsets(List.of(partition)).get(partition);
            if (endOffset == null) {
                return;
            }
            long lag = Math.max(0, endOffset - record.offset() - 1);
            logger.logConsumerLag(groupId(consumer), record.topic(), record.partition(), lag);
        } catch (RuntimeException ignored) {
            // Runtime logging must not affect Kafka consumption.
        }
    }

    private String groupId(Consumer<Object, Object> consumer) {
        try {
            return consumer.groupMetadata() == null ? "-" : consumer.groupMetadata().groupId();
        } catch (RuntimeException ex) {
            return "-";
        }
    }
}
