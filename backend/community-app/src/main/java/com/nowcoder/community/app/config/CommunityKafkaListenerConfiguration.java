package com.nowcoder.community.app.config;

import com.nowcoder.community.common.kafka.trace.TraceRecordInterceptor;
import com.nowcoder.community.common.spring.policy.KafkaPolicyDecisions;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.kafka.ConcurrentKafkaListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CompositeRecordInterceptor;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.RecordInterceptor;
import org.springframework.kafka.support.serializer.DeserializationException;
import org.springframework.util.backoff.ExponentialBackOff;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Configuration
@ConditionalOnClass(KafkaTemplate.class)
public class CommunityKafkaListenerConfiguration {

    @Bean
    public ConcurrentKafkaListenerContainerFactory<Object, Object> kafkaListenerContainerFactory(
            ConcurrentKafkaListenerContainerFactoryConfigurer configurer,
            ConsumerFactory<Object, Object> consumerFactory,
            DefaultErrorHandler communityKafkaDefaultErrorHandler,
            ObjectProvider<RecordInterceptor<Object, Object>> recordInterceptors
    ) {
        ConcurrentKafkaListenerContainerFactory<Object, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        configurer.configure(factory, consumerFactory);
        factory.setCommonErrorHandler(communityKafkaDefaultErrorHandler);
        factory.setRecordInterceptor(recordInterceptor(recordInterceptors));
        return factory;
    }

    @Bean
    public DefaultErrorHandler communityKafkaDefaultErrorHandler(
            KafkaTemplate<Object, Object> kafkaTemplate,
            KafkaPolicyDecisions decisions
    ) {
        Objects.requireNonNull(decisions, "Kafka policy decisions must not be null");
        if (!decisions.dlqEnabled()) {
            throw new IllegalStateException("community Kafka DLQ must be enabled");
        }
        DefaultErrorHandler handler = new DefaultErrorHandler(
                deadLetterPublishingRecoverer(kafkaTemplate),
                retryBackOff(decisions)
        );
        handler.addNotRetryableExceptions(DeserializationException.class);
        return handler;
    }

    ExponentialBackOff retryBackOff(KafkaPolicyDecisions decisions) {
        Objects.requireNonNull(decisions, "Kafka policy decisions must not be null");
        ExponentialBackOff backOff = new ExponentialBackOff(
                decisions.retryBaseBackoff().toMillis(),
                2.0
        );
        backOff.setMaxInterval(decisions.retryMaxBackoff().toMillis());
        backOff.setMaxAttempts(Math.max(0, decisions.retryMaxAttempts() - 1));
        return backOff;
    }

    DeadLetterPublishingRecoverer deadLetterPublishingRecoverer(
            KafkaTemplate<Object, Object> kafkaTemplate
    ) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                Objects.requireNonNull(kafkaTemplate, "Kafka template must not be null"),
                (record, exception) -> new TopicPartition(
                        record.topic() + ".dlq",
                        record.partition()
                )
        );
        recoverer.setAppendOriginalHeaders(true);
        recoverer.setRetainExceptionHeader(true);
        recoverer.setFailIfSendResultIsError(true);
        recoverer.setStripPreviousExceptionHeaders(false);
        return recoverer;
    }

    private RecordInterceptor<Object, Object> recordInterceptor(
            ObjectProvider<RecordInterceptor<Object, Object>> recordInterceptors
    ) {
        List<RecordInterceptor<Object, Object>> delegates = new ArrayList<>();
        delegates.add(new TraceRecordInterceptor());
        recordInterceptors.orderedStream()
                .filter(interceptor -> !(interceptor instanceof TraceRecordInterceptor))
                .forEach(delegates::add);
        if (delegates.size() == 1) {
            return delegates.get(0);
        }
        @SuppressWarnings("unchecked")
        RecordInterceptor<Object, Object>[] delegateArray =
                delegates.toArray(RecordInterceptor[]::new);
        return new CompositeRecordInterceptor<>(delegateArray);
    }
}
