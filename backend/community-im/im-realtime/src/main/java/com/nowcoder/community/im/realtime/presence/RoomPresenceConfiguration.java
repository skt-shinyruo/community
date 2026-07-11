package com.nowcoder.community.im.realtime.presence;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(RoomPresenceProperties.class)
public class RoomPresenceConfiguration {

    @Bean
    RoomPresenceDirectory redisRoomPresenceDirectory(
            StringRedisTemplate redisTemplate,
            RoomPresenceProperties properties
    ) {
        return new RedisRoomPresenceDirectory(redisTemplate, properties);
    }
}
