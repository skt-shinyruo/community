package com.nowcoder.community.im.realtime.kafka;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.kafka.ConcurrentKafkaListenerContainerFactoryConfigurer;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.RecordInterceptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class KafkaConfigTest {

    @Test
    void listenerFactoryKeepsAdditionalRecordInterceptors() {
        KafkaConfig config = new KafkaConfig();
        AtomicBoolean customInterceptorCalled = new AtomicBoolean(false);
        RecordInterceptor<Object, Object> customInterceptor = (record, consumer) -> {
            customInterceptorCalled.set(true);
            return record;
        };

        ConcurrentKafkaListenerContainerFactory<Object, Object> factory = config.kafkaListenerContainerFactory(
                mock(ConcurrentKafkaListenerContainerFactoryConfigurer.class),
                mock(ConsumerFactory.class),
                mock(DefaultErrorHandler.class),
                objectProvider(customInterceptor)
        );
        @SuppressWarnings("unchecked")
        RecordInterceptor<Object, Object> configuredInterceptor =
                (RecordInterceptor<Object, Object>) ReflectionTestUtils.getField(factory, "recordInterceptor");

        assertThat(configuredInterceptor).isNotNull();
        configuredInterceptor.intercept(new ConsumerRecord<>("im.topic", 0, 1L, "key", "value"), mock(Consumer.class));

        assertThat(customInterceptorCalled).isTrue();
    }

    private ObjectProvider<RecordInterceptor<Object, Object>> objectProvider(
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
