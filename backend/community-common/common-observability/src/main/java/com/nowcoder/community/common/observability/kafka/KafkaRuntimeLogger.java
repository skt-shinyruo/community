package com.nowcoder.community.common.observability.kafka;

import com.nowcoder.community.common.observability.logging.RuntimeLogEvent;
import com.nowcoder.community.common.observability.logging.RuntimeLogFields;
import com.nowcoder.community.common.observability.logging.RuntimeLogSanitizer;
import com.nowcoder.community.common.observability.logging.RuntimeLogWriter;
import com.nowcoder.community.common.observability.logging.RuntimeLoggingProperties;

public class KafkaRuntimeLogger {

    private final RuntimeLogWriter logWriter;
    private final RuntimeLoggingProperties properties;

    public KafkaRuntimeLogger(RuntimeLogWriter logWriter, RuntimeLoggingProperties properties) {
        this.logWriter = logWriter;
        this.properties = properties;
    }

    public void logProducerError(String topic, Integer partition, Throwable throwable) {
        RuntimeLogEvent.Builder builder = RuntimeLogEvent.builder("messaging", "kafka_producer_error", "failure", "kafka producer error")
                .field("messaging.system", "kafka")
                .field("messaging.destination.name", RuntimeLogSanitizer.text(topic))
                .field("messaging.kafka.partition", partition);
        if (throwable != null) {
            builder.field(RuntimeLogFields.ERROR_TYPE, throwable.getClass().getName());
        }
        logWriter.warn(builder.build());
    }

    public boolean logConsumerLag(String consumerGroup, String topic, int partition, long lag) {
        long threshold = properties.getKafka().getConsumerLagThreshold();
        if (lag < threshold) {
            return false;
        }
        logWriter.warn(RuntimeLogEvent.builder("messaging", "kafka_consumer_lag_threshold", "threshold", "kafka consumer lag threshold")
                .field("messaging.system", "kafka")
                .field("messaging.kafka.consumer.group", RuntimeLogSanitizer.text(consumerGroup))
                .field("messaging.destination.name", RuntimeLogSanitizer.text(topic))
                .field("messaging.kafka.partition", partition)
                .field("messaging.kafka.consumer.lag", lag)
                .field("threshold.count", threshold)
                .build());
        return true;
    }

    public void logRebalance(String consumerGroup, String reason, String topic, int partitionCount) {
        logWriter.info(RuntimeLogEvent.builder("messaging", "kafka_rebalance", "success", "kafka rebalance")
                .field("messaging.system", "kafka")
                .field("messaging.kafka.consumer.group", RuntimeLogSanitizer.text(consumerGroup))
                .field("messaging.kafka.rebalance.reason", RuntimeLogSanitizer.operation(reason))
                .field("messaging.destination.name", RuntimeLogSanitizer.text(topic))
                .field("messaging.kafka.partition.count", partitionCount)
                .build());
    }
}
