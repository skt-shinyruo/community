package com.nowcoder.community.common.observability.kafka;

import com.nowcoder.community.common.observability.logging.RuntimeLogFields;
import com.nowcoder.community.common.observability.logging.RuntimeLogTestSupport;
import com.nowcoder.community.common.observability.logging.RuntimeLoggingProperties;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerGroupMetadata;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RuntimeKafkaInstrumentationTest {

    @Test
    void producerListenerLogsErrorsWithoutMessageBody() {
        try (RuntimeLogTestSupport.Capture capture = RuntimeLogTestSupport.capture("test.kafka-producer-runtime")) {
            RuntimeLoggingProperties properties = new RuntimeLoggingProperties();
            KafkaRuntimeLogger logger = new KafkaRuntimeLogger(capture.writer(), properties);
            RuntimeKafkaProducerListener listener = new RuntimeKafkaProducerListener(logger);

            listener.onError(new ProducerRecord<>("topic-a", 1, "key", "secret-body"), null, new IllegalStateException("boom"));

            assertThat(capture.appender().list).hasSize(1);
            assertThat(capture.appender().list.get(0).getMDCPropertyMap())
                    .containsEntry(RuntimeLogFields.EVENT_ACTION, "kafka_producer_error")
                    .containsEntry("messaging.destination.name", "topic-a")
                    .containsEntry("messaging.kafka.partition", "1")
                    .doesNotContainValue("secret-body");
        }
    }

    @Test
    void rebalanceListenerLogsTopicPartitionSummary() {
        try (RuntimeLogTestSupport.Capture capture = RuntimeLogTestSupport.capture("test.kafka-rebalance-runtime")) {
            RuntimeLoggingProperties properties = new RuntimeLoggingProperties();
            KafkaRuntimeLogger logger = new KafkaRuntimeLogger(capture.writer(), properties);
            RuntimeKafkaRebalanceListener listener = new RuntimeKafkaRebalanceListener(logger);
            Consumer<?, ?> consumer = mock(Consumer.class);
            when(consumer.groupMetadata()).thenReturn(new ConsumerGroupMetadata("group-a"));

            listener.onPartitionsAssigned(consumer, List.of(new TopicPartition("topic-a", 0), new TopicPartition("topic-a", 1)));

            assertThat(capture.appender().list).hasSize(1);
            assertThat(capture.appender().list.get(0).getMDCPropertyMap())
                    .containsEntry(RuntimeLogFields.EVENT_ACTION, "kafka_rebalance")
                    .containsEntry("messaging.kafka.consumer.group", "group-a")
                    .containsEntry("messaging.kafka.rebalance.reason", "assigned")
                    .containsEntry("messaging.destination.name", "topic-a")
                    .containsEntry("messaging.kafka.partition.count", "2");
        }
    }

    @Test
    void recordInterceptorLogsLagAboveThresholdWithoutMessageBody() {
        try (RuntimeLogTestSupport.Capture capture = RuntimeLogTestSupport.capture("test.kafka-lag-runtime")) {
            RuntimeLoggingProperties properties = new RuntimeLoggingProperties();
            properties.getKafka().setConsumerLagThreshold(3);
            KafkaRuntimeLogger logger = new KafkaRuntimeLogger(capture.writer(), properties);
            AtomicLong now = new AtomicLong(0);
            RuntimeKafkaRecordInterceptor interceptor = new RuntimeKafkaRecordInterceptor(logger, now::get);
            Consumer<Object, Object> consumer = mock(Consumer.class);
            TopicPartition partition = new TopicPartition("topic-a", 0);
            when(consumer.groupMetadata()).thenReturn(new ConsumerGroupMetadata("group-a"));
            when(consumer.endOffsets(List.of(partition))).thenReturn(Map.of(partition, 10L));

            ConsumerRecord<Object, Object> record = new ConsumerRecord<>("topic-a", 0, 5L, "key", "secret-body");
            assertThat(interceptor.intercept(record, consumer)).isSameAs(record);

            assertThat(capture.appender().list).hasSize(1);
            assertThat(capture.appender().list.get(0).getMDCPropertyMap())
                    .containsEntry(RuntimeLogFields.EVENT_ACTION, "kafka_consumer_lag_threshold")
                    .containsEntry("messaging.kafka.consumer.group", "group-a")
                    .containsEntry("messaging.kafka.consumer.lag", "4")
                    .doesNotContainValue("secret-body");
        }
    }
}
