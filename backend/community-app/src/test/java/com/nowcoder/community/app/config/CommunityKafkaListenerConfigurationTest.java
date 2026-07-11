package com.nowcoder.community.app.config;

import com.nowcoder.community.common.spring.policy.KafkaPolicyDecisions;
import com.nowcoder.community.common.spring.policy.KafkaPolicyProperties;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.kafka.ConcurrentKafkaListenerContainerFactoryConfigurer;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.RecordInterceptor;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.backoff.BackOffExecution;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CommunityKafkaListenerConfigurationTest {

    private final CommunityKafkaListenerConfiguration configuration =
            new CommunityKafkaListenerConfiguration();

    @Test
    void retryBackOffShouldTreatConfiguredAttemptsAsTotalDeliveries() {
        BackOffExecution execution = configuration.retryBackOff(decisions(3, true)).start();

        assertThat(execution.nextBackOff()).isEqualTo(100L);
        assertThat(execution.nextBackOff()).isEqualTo(200L);
        assertThat(execution.nextBackOff()).isEqualTo(BackOffExecution.STOP);
    }

    @Test
    void oneTotalAttemptShouldNotScheduleARetry() {
        BackOffExecution execution = configuration.retryBackOff(decisions(1, true)).start();

        assertThat(execution.nextBackOff()).isEqualTo(BackOffExecution.STOP);
    }

    @Test
    void deadLetterRecovererShouldPreserveSourceMetadataAndPartition() {
        KafkaTemplate<Object, Object> kafkaTemplate = mock(KafkaTemplate.class);
        when(kafkaTemplate.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));
        DeadLetterPublishingRecoverer recoverer = configuration.deadLetterPublishingRecoverer(kafkaTemplate);
        ConsumerRecord<Object, Object> source =
                new ConsumerRecord<>("content.events", 4, 19L, "key", "value");

        recoverer.accept(source, null, new IllegalArgumentException("bad payload"));

        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<ProducerRecord<Object, Object>> captor =
                org.mockito.ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate).send(captor.capture());
        ProducerRecord<Object, Object> dlq = captor.getValue();
        assertThat(dlq.topic()).isEqualTo("content.events.dlq");
        assertThat(dlq.partition()).isEqualTo(4);
        assertThat(stringHeader(dlq, KafkaHeaders.DLT_ORIGINAL_TOPIC)).isEqualTo("content.events");
        assertThat(intHeader(dlq, KafkaHeaders.DLT_ORIGINAL_PARTITION)).isEqualTo(4);
        assertThat(longHeader(dlq, KafkaHeaders.DLT_ORIGINAL_OFFSET)).isEqualTo(19L);
        assertThat(stringHeader(dlq, KafkaHeaders.DLT_EXCEPTION_FQCN))
                .isEqualTo(IllegalArgumentException.class.getName());
    }

    @Test
    void deadLetterRecovererShouldPropagateKafkaSendFailure() {
        KafkaTemplate<Object, Object> kafkaTemplate = mock(KafkaTemplate.class);
        IllegalStateException sendFailure = new IllegalStateException("broker unavailable");
        CompletableFuture<SendResult<Object, Object>> failed = new CompletableFuture<>();
        failed.completeExceptionally(sendFailure);
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(failed);
        DeadLetterPublishingRecoverer recoverer = configuration.deadLetterPublishingRecoverer(kafkaTemplate);

        assertThatThrownBy(() -> recoverer.accept(
                new ConsumerRecord<>("content.events", 4, 19L, "key", "value"),
                null,
                new IllegalArgumentException("bad payload")
        ))
                .isInstanceOf(KafkaException.class)
                .hasRootCause(sendFailure);
    }

    @Test
    void disabledDlqShouldFailErrorHandlerCreation() {
        assertThatThrownBy(() -> configuration.communityKafkaDefaultErrorHandler(
                mock(KafkaTemplate.class), decisions(3, false)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DLQ");
    }

    @Test
    void listenerFactoryShouldKeepAdditionalRecordInterceptors() {
        AtomicBoolean customInterceptorCalled = new AtomicBoolean(false);
        RecordInterceptor<Object, Object> customInterceptor = (record, consumer) -> {
            customInterceptorCalled.set(true);
            return record;
        };
        ConcurrentKafkaListenerContainerFactory<Object, Object> factory =
                configuration.kafkaListenerContainerFactory(
                        mock(ConcurrentKafkaListenerContainerFactoryConfigurer.class),
                        mock(ConsumerFactory.class),
                        mock(DefaultErrorHandler.class),
                        objectProvider(customInterceptor)
                );
        @SuppressWarnings("unchecked")
        RecordInterceptor<Object, Object> configuredInterceptor =
                (RecordInterceptor<Object, Object>) ReflectionTestUtils.getField(factory, "recordInterceptor");
        ConsumerRecord<Object, Object> record = new ConsumerRecord<>("content.events", 0, 1L, "key", "value");
        Consumer<Object, Object> consumer = mock(Consumer.class);

        assertThat(configuredInterceptor).isNotNull();
        configuredInterceptor.intercept(record, consumer);
        configuredInterceptor.afterRecord(record, consumer);

        assertThat(customInterceptorCalled).isTrue();
    }

    private static KafkaPolicyDecisions decisions(int maxAttempts, boolean dlqEnabled) {
        KafkaPolicyProperties properties = new KafkaPolicyProperties();
        properties.getRetry().setMaxAttempts(maxAttempts);
        properties.getRetry().setBaseBackoff(Duration.ofMillis(100));
        properties.getRetry().setMaxBackoff(Duration.ofSeconds(1));
        properties.getDlq().setEnabled(dlqEnabled);
        return new KafkaPolicyDecisions(properties);
    }

    private static String stringHeader(ProducerRecord<?, ?> record, String key) {
        Header header = Objects.requireNonNull(record.headers().lastHeader(key));
        return new String(header.value(), StandardCharsets.UTF_8);
    }

    private static int intHeader(ProducerRecord<?, ?> record, String key) {
        Header header = Objects.requireNonNull(record.headers().lastHeader(key));
        return ByteBuffer.wrap(header.value()).getInt();
    }

    private static long longHeader(ProducerRecord<?, ?> record, String key) {
        Header header = Objects.requireNonNull(record.headers().lastHeader(key));
        return ByteBuffer.wrap(header.value()).getLong();
    }

    private static ObjectProvider<RecordInterceptor<Object, Object>> objectProvider(
            RecordInterceptor<Object, Object> interceptor
    ) {
        return new ObjectProvider<>() {
            @Override
            public RecordInterceptor<Object, Object> getObject(Object... args) {
                return interceptor;
            }

            @Override
            public RecordInterceptor<Object, Object> getIfAvailable() {
                return interceptor;
            }

            @Override
            public RecordInterceptor<Object, Object> getIfUnique() {
                return interceptor;
            }

            @Override
            public RecordInterceptor<Object, Object> getObject() {
                return interceptor;
            }

            @Override
            public Stream<RecordInterceptor<Object, Object>> orderedStream() {
                return Stream.of(interceptor);
            }
        };
    }
}
