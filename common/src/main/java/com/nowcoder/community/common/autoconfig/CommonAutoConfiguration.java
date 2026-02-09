package com.nowcoder.community.common.autoconfig;

import com.nowcoder.community.common.startup.StartupValidationAutoConfig;
import com.nowcoder.community.common.idempotency.IdempotencyGuard;
import com.nowcoder.community.common.idempotency.IdempotencyProperties;
import com.nowcoder.community.common.idempotency.IdempotencyStore;
import com.nowcoder.community.common.idempotency.JdbcIdempotencyStore;
import com.nowcoder.community.common.idempotency.RedisIdempotencyStore;
import com.nowcoder.community.common.net.TrustedProxyProperties;
import com.nowcoder.community.common.web.CommonJacksonConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * common 通用自动装配：
 * - 不依赖 servlet/reactive 细节（可在所有服务中生效，包括 gateway）
 * - 提供跨服务一致的基础能力（如 Jackson 时间序列化、启动期校验等）
 */
@AutoConfiguration
@EnableConfigurationProperties({
        TrustedProxyProperties.class,
        IdempotencyProperties.class
})
@Import({
        CommonJacksonConfig.class,
        StartupValidationAutoConfig.class
})
public class CommonAutoConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "http.idempotency", name = "enabled", havingValue = "true")
    @ConditionalOnMissingBean
    public IdempotencyStore idempotencyStore(
            IdempotencyProperties properties,
            ObjectProvider<JdbcTemplate> jdbcTemplateProvider,
            ObjectProvider<StringRedisTemplate> redisTemplateProvider
    ) {
        IdempotencyProperties.Store store = properties == null ? IdempotencyProperties.Store.REDIS : properties.getStore();
        if (store == IdempotencyProperties.Store.DB) {
            JdbcTemplate jdbcTemplate = jdbcTemplateProvider == null ? null : jdbcTemplateProvider.getIfAvailable();
            if (jdbcTemplate == null) {
                throw new IllegalStateException("http.idempotency.store=DB 需要 JdbcTemplate");
            }
            return new JdbcIdempotencyStore(jdbcTemplate);
        }
        StringRedisTemplate redisTemplate = redisTemplateProvider == null ? null : redisTemplateProvider.getIfAvailable();
        if (redisTemplate == null) {
            throw new IllegalStateException("http.idempotency.store=REDIS 需要 StringRedisTemplate");
        }
        return new RedisIdempotencyStore(redisTemplate);
    }

    @Bean
    @ConditionalOnProperty(prefix = "http.idempotency", name = "enabled", havingValue = "true")
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
