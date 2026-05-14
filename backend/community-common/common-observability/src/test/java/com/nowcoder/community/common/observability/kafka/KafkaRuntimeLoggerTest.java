package com.nowcoder.community.common.observability.kafka;

import com.nowcoder.community.common.observability.logging.RuntimeLogFields;
import com.nowcoder.community.common.observability.logging.RuntimeLogTestSupport;
import com.nowcoder.community.common.observability.logging.RuntimeLoggingProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaRuntimeLoggerTest {

    @Test
    void logsProducerErrorLagThresholdAndRebalanceWithoutPayload() {
        try (RuntimeLogTestSupport.Capture capture = RuntimeLogTestSupport.capture("test.kafka-runtime")) {
            RuntimeLoggingProperties properties = new RuntimeLoggingProperties();
            properties.getKafka().setConsumerLagThreshold(100);
            KafkaRuntimeLogger logger = new KafkaRuntimeLogger(capture.writer(), properties);

            logger.logProducerError("im.policy.snapshot", 1, new IllegalStateException("send failed"));
            assertThat(logger.logConsumerLag("group-a", "im.policy.snapshot", 2, 99)).isFalse();
            assertThat(logger.logConsumerLag("group-a", "im.policy.snapshot", 2, 100)).isTrue();
            logger.logRebalance("group-a", "assigned", "im.policy.snapshot", 2);

            assertThat(capture.appender().list).hasSize(3);
            assertThat(capture.appender().list.get(0).getMDCPropertyMap())
                    .containsEntry(RuntimeLogFields.COMMUNITY_CATEGORY, "messaging")
                    .containsEntry(RuntimeLogFields.COMMUNITY_ACTION, "kafka_producer_error")
                    .containsEntry("messaging.system", "kafka")
                    .containsEntry("messaging.destination.name", "im.policy.snapshot")
                    .containsEntry("messaging.kafka.partition", "1")
                    .containsEntry("error.type", IllegalStateException.class.getName())
                    .doesNotContainKey("messaging.message.payload");
            assertThat(capture.appender().list.get(1).getMDCPropertyMap())
                    .containsEntry(RuntimeLogFields.COMMUNITY_ACTION, "kafka_consumer_lag_threshold")
                    .containsEntry("messaging.kafka.consumer.group", "group-a")
                    .containsEntry("messaging.kafka.consumer.lag", "100")
                    .containsEntry("threshold.count", "100");
            assertThat(capture.appender().list.get(2).getMDCPropertyMap())
                    .containsEntry(RuntimeLogFields.COMMUNITY_ACTION, "kafka_rebalance")
                    .containsEntry("messaging.kafka.rebalance.reason", "assigned");
        }
    }
}
