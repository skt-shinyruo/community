package com.nowcoder.community.im.realtime.fanout;

import com.nowcoder.community.im.realtime.presence.NoopRoomPresenceDirectory;
import com.nowcoder.community.im.realtime.presence.RedisRoomPresenceDirectory;
import com.nowcoder.community.im.realtime.presence.RoomPresenceDirectory;
import com.nowcoder.community.im.realtime.presence.RoomPresenceProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class RoomFanoutConfigurationTest {

    @Test
    void routedModeFailsFastWhenPresenceDirectoryIsNoop() {
        new ApplicationContextRunner()
                .withUserConfiguration(NoopPresenceConfiguration.class)
                .withPropertyValues("im.room-fanout.mode=routed")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(IllegalStateException.class)
                            .rootCause()
                            .hasMessageContaining("routed")
                            .hasMessageContaining("distributed room presence");
                });
    }

    @Test
    void routedModeStartsWhenPresenceDirectoryIsRedisBacked() {
        new ApplicationContextRunner()
                .withUserConfiguration(RedisPresenceConfiguration.class)
                .withPropertyValues("im.room-fanout.mode=routed")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(RoomFanoutProperties.class);
                });
    }

    @Configuration(proxyBeanMethods = false)
    @Import(RoomFanoutConfiguration.class)
    static class NoopPresenceConfiguration {

        @Bean
        RoomPresenceDirectory roomPresenceDirectory() {
            return new NoopRoomPresenceDirectory();
        }
    }

    @Configuration(proxyBeanMethods = false)
    @Import(RoomFanoutConfiguration.class)
    static class RedisPresenceConfiguration {

        @Bean
        StringRedisTemplate stringRedisTemplate() {
            return mock(StringRedisTemplate.class);
        }

        @Bean
        RoomPresenceDirectory roomPresenceDirectory(
                StringRedisTemplate redisTemplate
        ) {
            return new RedisRoomPresenceDirectory(redisTemplate, new RoomPresenceProperties());
        }
    }
}
