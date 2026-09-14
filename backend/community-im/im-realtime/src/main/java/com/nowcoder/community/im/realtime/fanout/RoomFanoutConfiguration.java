package com.nowcoder.community.im.realtime.fanout;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(RoomFanoutProperties.class)
public class RoomFanoutConfiguration {

    @Bean
    RoomFanoutPropertiesValidator roomFanoutPropertiesValidator(RoomFanoutProperties fanoutProperties) {
        return new RoomFanoutPropertiesValidator(fanoutProperties);
    }

    static final class RoomFanoutPropertiesValidator {

        RoomFanoutPropertiesValidator(RoomFanoutProperties fanoutProperties) {
            fanoutProperties.normalizedWorkerInboxSlot();
            fanoutProperties.normalizedPublishTimeout();
        }
    }
}
