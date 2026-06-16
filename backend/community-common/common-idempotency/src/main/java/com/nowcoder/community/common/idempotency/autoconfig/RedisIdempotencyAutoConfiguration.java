package com.nowcoder.community.common.idempotency.autoconfig;

import com.nowcoder.community.common.idempotency.IdempotencyStore;
import com.nowcoder.community.common.idempotency.RedisIdempotencyStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

@AutoConfiguration(after = RedisAutoConfiguration.class)
@ConditionalOnProperty(prefix = "http.idempotency", name = "enabled", havingValue = "true")
@ConditionalOnClass(StringRedisTemplate.class)
public class RedisIdempotencyAutoConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "http.idempotency", name = "store", havingValue = "REDIS", matchIfMissing = true)
    @ConditionalOnMissingBean
    public IdempotencyStore idempotencyStore(ObjectProvider<StringRedisTemplate> redisTemplateProvider) {
        StringRedisTemplate redisTemplate = redisTemplateProvider == null ? null : redisTemplateProvider.getIfAvailable();
        if (redisTemplate == null) {
            throw new IllegalStateException("http.idempotency.store=REDIS 需要 StringRedisTemplate");
        }
        return new RedisIdempotencyStore(redisTemplate);
    }
}
