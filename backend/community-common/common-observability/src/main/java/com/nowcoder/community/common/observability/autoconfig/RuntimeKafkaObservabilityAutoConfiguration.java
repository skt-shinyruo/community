package com.nowcoder.community.common.observability.autoconfig;

import com.nowcoder.community.common.observability.kafka.KafkaRuntimeLogger;
import com.nowcoder.community.common.observability.kafka.RuntimeKafkaProducerListener;
import com.nowcoder.community.common.observability.kafka.RuntimeKafkaRebalanceListener;
import com.nowcoder.community.common.observability.kafka.RuntimeKafkaRecordInterceptor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.listener.ConsumerAwareRebalanceListener;
import org.springframework.kafka.listener.RecordInterceptor;
import org.springframework.kafka.support.ProducerListener;

@AutoConfiguration(
        after = RuntimeObservabilityAutoConfiguration.class,
        beforeName = {
                "org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration",
                "org.springframework.boot.autoconfigure.kafka.KafkaAnnotationDrivenConfiguration"
        }
)
@ConditionalOnClass(name = "org.springframework.kafka.core.KafkaTemplate")
public class RuntimeKafkaObservabilityAutoConfiguration {

    private static final String PREFIX = "community.observability.runtime-logging";

    @Bean
    @ConditionalOnMissingBean(ProducerListener.class)
    @ConditionalOnBean(KafkaRuntimeLogger.class)
    @ConditionalOnProperty(prefix = PREFIX + ".kafka", name = "enabled", havingValue = "true", matchIfMissing = true)
    public RuntimeKafkaProducerListener runtimeKafkaProducerListener(KafkaRuntimeLogger logger) {
        return new RuntimeKafkaProducerListener(logger);
    }

    @Bean
    @ConditionalOnMissingBean(ConsumerAwareRebalanceListener.class)
    @ConditionalOnBean(KafkaRuntimeLogger.class)
    @ConditionalOnProperty(prefix = PREFIX + ".kafka", name = "enabled", havingValue = "true", matchIfMissing = true)
    public RuntimeKafkaRebalanceListener runtimeKafkaRebalanceListener(KafkaRuntimeLogger logger) {
        return new RuntimeKafkaRebalanceListener(logger);
    }

    @Bean
    @ConditionalOnMissingBean(RecordInterceptor.class)
    @ConditionalOnBean(KafkaRuntimeLogger.class)
    @ConditionalOnProperty(prefix = PREFIX + ".kafka", name = "enabled", havingValue = "true", matchIfMissing = true)
    public RuntimeKafkaRecordInterceptor runtimeKafkaRecordInterceptor(KafkaRuntimeLogger logger) {
        return new RuntimeKafkaRecordInterceptor(logger);
    }
}
