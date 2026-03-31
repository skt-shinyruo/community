package com.nowcoder.community.common.idempotency.autoconfig;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.common.idempotency.IdempotencyGuard;
import com.nowcoder.community.common.idempotency.IdempotencyProperties;
import com.nowcoder.community.common.idempotency.IdempotencyStore;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(after = {
        JdbcIdempotencyAutoConfiguration.class,
        RedisIdempotencyAutoConfiguration.class
})
@ConditionalOnProperty(prefix = "http.idempotency", name = "enabled", havingValue = "true")
@ConditionalOnBean(IdempotencyStore.class)
@ConditionalOnClass(ObjectMapper.class)
@EnableConfigurationProperties(IdempotencyProperties.class)
public class IdempotencyGuardAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public IdempotencyGuard idempotencyGuard(
            ObjectMapper objectMapper,
            IdempotencyStore store,
            ObjectProvider<MeterRegistry> meterRegistryProvider,
            IdempotencyProperties properties
    ) {
        return new IdempotencyGuard(objectMapper, store, meterRegistryProvider, properties);
    }
}
