package com.nowcoder.community.infra.idempotency;

import com.nowcoder.community.common.idempotency.IdempotencyGuard;
import com.nowcoder.community.common.idempotency.IdempotencyStore;
import com.nowcoder.community.common.idempotency.IdempotencyProperties;
import com.nowcoder.community.common.idempotency.JdbcIdempotencyStore;
import com.nowcoder.community.common.json.JacksonJsonCodec;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Wires the JDBC-backed idempotency guard for community-app. The module has a
 * single consumer, so a plain configuration replaces the starter split.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "http.idempotency", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(IdempotencyProperties.class)
public class IdempotencyConfiguration {

    @Bean
    @ConditionalOnMissingBean(IdempotencyStore.class)
    public com.nowcoder.community.common.idempotency.TransactionalIdempotencyStore idempotencyStore(
            JdbcTemplate jdbcTemplate
    ) {
        return new JdbcIdempotencyStore(jdbcTemplate);
    }

    @Bean
    @ConditionalOnMissingBean
    public IdempotencyGuard idempotencyGuard(
            JacksonJsonCodec jsonCodec,
            IdempotencyStore store,
            ObjectProvider<MeterRegistry> meterRegistryProvider,
            IdempotencyProperties properties
    ) {
        return new IdempotencyGuard(jsonCodec, store, meterRegistryProvider, properties);
    }
}
