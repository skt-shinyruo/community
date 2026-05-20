package com.nowcoder.community.im.core.kafka;

import com.nowcoder.community.common.logging.EventLogFields;
import com.nowcoder.community.common.kafka.trace.TraceRecordInterceptor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.boot.autoconfigure.kafka.ConcurrentKafkaListenerContainerFactoryConfigurer;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CompositeRecordInterceptor;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.RecordInterceptor;
import org.springframework.kafka.support.serializer.DeserializationException;
import org.springframework.util.backoff.FixedBackOff;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Configuration
@ConditionalOnClass(KafkaTemplate.class)
public class KafkaConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaConfig.class);
    private static final String CATEGORY_ASYNC = "async";
    private static final String MDC_CATEGORY = EventLogFields.EVENT_CATEGORY;
    private static final String MDC_ACTION = EventLogFields.EVENT_ACTION;
    private static final String MDC_OUTCOME = EventLogFields.EVENT_OUTCOME;

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
                    warnEvent(
                            "kafka_dlq_recover",
                            "degraded",
                            null,
                            "community.source_topic", record.topic(),
                            "community.dlq_topic", dlqTopic,
                            "community.kafka_partition", record.partition(),
                            "community.kafka_offset", record.offset(),
                            "community.reason_code", exceptionReasonCode(ex),
                            "community.error_class", errorClass(ex),
                            "community.error_message", errorMessage(ex)
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

    private void warnEvent(String action, String outcome, Throwable throwable, Object... keyValues) {
        logEvent(action, outcome, true, throwable, keyValues);
    }

    private void logEvent(String action, String outcome, boolean warn, Throwable throwable, Object... keyValues) {
        if (keyValues.length % 2 != 0) {
            throw new IllegalArgumentException("Kafka event keyValues must contain key/value pairs");
        }
        String previousCategory = MDC.get(MDC_CATEGORY);
        String previousAction = MDC.get(MDC_ACTION);
        String previousOutcome = MDC.get(MDC_OUTCOME);
        MDC.put(MDC_CATEGORY, CATEGORY_ASYNC);
        MDC.put(MDC_ACTION, action);
        MDC.put(MDC_OUTCOME, outcome);
        try {
            String message = buildMessage(action, outcome, keyValues);
            if (warn) {
                if (throwable == null) {
                    log.warn(message);
                } else {
                    log.warn(message, throwable);
                }
                return;
            }
            log.info(message);
        } finally {
            restore(MDC_CATEGORY, previousCategory);
            restore(MDC_ACTION, previousAction);
            restore(MDC_OUTCOME, previousOutcome);
        }
    }

    private String buildMessage(String action, String outcome, Object... keyValues) {
        StringBuilder message = new StringBuilder(160);
        for (int i = 0; i < keyValues.length; i += 2) {
            appendToken(message, String.valueOf(keyValues[i]), keyValues[i + 1]);
        }
        return message.toString();
    }

    private void appendToken(StringBuilder message, String key, Object value) {
        if (message.length() > 0) {
            message.append(' ');
        }
        message.append(key).append('=').append(encodeTokenValue(value));
    }

    private String encodeTokenValue(Object value) {
        if (value == null) {
            return "-";
        }
        String raw = String.valueOf(value);
        if (raw.isEmpty()) {
            return "-";
        }
        StringBuilder encoded = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char ch = raw.charAt(i);
            if (Character.isWhitespace(ch) || Character.isISOControl(ch) || ch == '=' || ch == '%') {
                encoded.append('%');
                String hex = Integer.toHexString(ch).toUpperCase(Locale.ROOT);
                if (hex.length() == 1) {
                    encoded.append('0');
                }
                encoded.append(hex);
            } else {
                encoded.append(ch);
            }
        }
        return encoded.toString();
    }

    private String exceptionReasonCode(Throwable throwable) {
        if (throwable == null) {
            return "unknown";
        }
        String simpleName = throwable.getClass().getSimpleName();
        if (simpleName.endsWith("Exception")) {
            simpleName = simpleName.substring(0, simpleName.length() - "Exception".length());
        } else if (simpleName.endsWith("Error")) {
            simpleName = simpleName.substring(0, simpleName.length() - "Error".length());
        }
        if (simpleName.isEmpty()) {
            return "unknown";
        }
        StringBuilder out = new StringBuilder(simpleName.length() + 8);
        for (int i = 0; i < simpleName.length(); i++) {
            char ch = simpleName.charAt(i);
            if (Character.isUpperCase(ch) && i > 0) {
                out.append('_');
            }
            out.append(Character.toLowerCase(ch));
        }
        return out.toString();
    }

    private String errorClass(Throwable throwable) {
        return throwable == null ? null : throwable.getClass().getName();
    }

    private String errorMessage(Throwable throwable) {
        return throwable == null ? null : throwable.getMessage();
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
        RecordInterceptor<Object, Object>[] delegateArray = delegates.toArray(RecordInterceptor[]::new);
        return new CompositeRecordInterceptor<>(delegateArray);
    }

    private void restore(String key, String previousValue) {
        if (previousValue == null) {
            MDC.remove(key);
            return;
        }
        MDC.put(key, previousValue);
    }
}
