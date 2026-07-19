package com.nowcoder.community.common.idempotency.autoconfig;

import com.nowcoder.community.common.idempotency.IdempotencyGuard;
import com.nowcoder.community.common.idempotency.IdempotencyProperties;
import com.nowcoder.community.common.idempotency.IdempotencyStore;
import com.nowcoder.community.common.json.JsonCodec;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(
        after = {
                JdbcIdempotencyAutoConfiguration.class,
                RedisIdempotencyAutoConfiguration.class
        },
        afterName = {
                "com.nowcoder.community.common.web.autoconfig.ServletWebInfraAutoConfiguration",
                "com.nowcoder.community.common.webflux.autoconfig.WebFluxInfraAutoConfiguration"
        }
)
@ConditionalOnProperty(prefix = "http.idempotency", name = "enabled", havingValue = "true")
@ConditionalOnBean({IdempotencyStore.class, JsonCodec.class})
@ConditionalOnClass(JsonCodec.class)
@EnableConfigurationProperties(IdempotencyProperties.class)
public class IdempotencyGuardAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public IdempotencyGuard idempotencyGuard(
            JsonCodec jsonCodec,
            IdempotencyStore store,
            ObjectProvider<MeterRegistry> meterRegistryProvider,
            IdempotencyProperties properties
    ) {
        // Keep Redis configuration bootable; the guard rejects non-transactional stores before executing a write.
        return new IdempotencyGuard(jsonCodec, store, meterRegistryProvider, properties);
    }
}
