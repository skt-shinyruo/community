package com.nowcoder.community.im.realtime.kafka;

import com.nowcoder.community.common.kafka.trace.TraceRecordInterceptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.kafka.ConcurrentKafkaListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CompositeRecordInterceptor;
import org.springframework.kafka.listener.RecordInterceptor;

import java.util.ArrayList;
import java.util.List;

@Configuration
@ConditionalOnClass(KafkaTemplate.class)
public class KafkaConfig {

    @Bean
    public ConcurrentKafkaListenerContainerFactory<Object, Object> kafkaListenerContainerFactory(
            ConcurrentKafkaListenerContainerFactoryConfigurer configurer,
            ConsumerFactory<Object, Object> consumerFactory,
            ObjectProvider<RecordInterceptor<Object, Object>> recordInterceptors
    ) {
        ConcurrentKafkaListenerContainerFactory<Object, Object> factory = new ConcurrentKafkaListenerContainerFactory<>();
        configurer.configure(factory, consumerFactory);
        factory.setRecordInterceptor(recordInterceptor(recordInterceptors));
        return factory;
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
}
