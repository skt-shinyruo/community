package com.nowcoder.community.infra.scheduler.autoconfig;

import com.nowcoder.community.infra.scheduler.SingleFlightTaskGuard;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

@AutoConfiguration
public class SchedulerInfraAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(StringRedisTemplate.class)
    public SingleFlightTaskGuard singleFlightTaskGuard(StringRedisTemplate redisTemplate) {
        return new SingleFlightTaskGuard(redisTemplate);
    }
}
