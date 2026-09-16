package com.nowcoder.community.im.core.kafka;

import com.nowcoder.community.common.kafka.trace.TraceRecordInterceptor;
import com.nowcoder.community.common.logging.AsyncEventLogger;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.boot.kafka.autoconfigure.ConcurrentKafkaListenerContainerFactoryConfigurer;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.RecordInterceptor;
import org.springframework.kafka.support.serializer.DeserializationException;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaConfig.class);

    @Bean
    public ConcurrentKafkaListenerContainerFactory<Object, Object> kafkaListenerContainerFactory(
            ConcurrentKafkaListenerContainerFactoryConfigurer configurer,
            ConsumerFactory<Object, Object> consumerFactory,
            DefaultErrorHandler errorHandler,
            ObjectProvider<RecordInterceptor<Object, Object>> recordInterceptors
    ) {
        ConcurrentKafkaListenerContainerFactory<Object, Object> factory = new ConcurrentKafkaListenerContainerFactory<>();
        configurer.configure(factory, consumerFactory);
        factory.setCommonErrorHandler(errorHandler);
        factory.setRecordInterceptor(recordInterceptor(recordInterceptors));
        return factory;
    }

    @Bean
    public DefaultErrorHandler kafkaDefaultErrorHandler(KafkaTemplate<Object, Object> kafkaTemplate) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (ConsumerRecord<?, ?> record, Exception ex) -> {
                    String dlqTopic = record.topic() + ".dlq";
                    AsyncEventLogger.warn(
                            log,
                            "kafka_dlq_recover",
                            "degraded",
                            "community.source_topic", record.topic(),
                            "community.dlq_topic", dlqTopic,
                            "community.kafka_partition", record.partition(),
                            "community.kafka_offset", record.offset(),
                            "community.reason_code", AsyncEventLogger.exceptionReasonCode(ex),
                            "community.error_class", AsyncEventLogger.errorClass(ex),
                            "community.error_message", AsyncEventLogger.errorMessage(ex)
                    );
                    return new TopicPartition(dlqTopic, record.partition());
                }
        );

        // Retry transient processing failures before DLQ; validation failures remain non-retryable below.
        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 3L));

        // Treat common validation errors as non-retryable.
        handler.addNotRetryableExceptions(
                DeserializationException.class,
                IllegalArgumentException.class,
                SecurityException.class
        );
        return handler;
    }

    private RecordInterceptor<Object, Object> recordInterceptor(
            ObjectProvider<RecordInterceptor<Object, Object>> recordInterceptors
    ) {
        @SuppressWarnings({"unchecked", "rawtypes"})
        RecordInterceptor<Object, Object>[] delegates = recordInterceptors.stream().toArray(RecordInterceptor[]::new);
        return TraceRecordInterceptor.composeFirst(delegates);
    }
}
