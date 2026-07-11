package com.nowcoder.community.im.realtime.presence;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(RoomPresenceProperties.class)
public class RoomPresenceConfiguration {

    @Bean
    @ConditionalOnBean(StringRedisTemplate.class)
    @ConditionalOnProperty(prefix = "im.room-presence", name = "enabled", havingValue = "true")
    RoomPresenceDirectory redisRoomPresenceDirectory(
            StringRedisTemplate redisTemplate,
            RoomPresenceProperties properties
    ) {
        return new RedisRoomPresenceDirectory(redisTemplate, properties);
    }
}
