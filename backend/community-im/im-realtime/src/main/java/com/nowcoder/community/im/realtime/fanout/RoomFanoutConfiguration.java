package com.nowcoder.community.im.realtime.fanout;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(RoomFanoutProperties.class)
public class RoomFanoutConfiguration {

    @Bean
    RoomFanoutInboxSlotValidator roomFanoutInboxSlotValidator(RoomFanoutProperties fanoutProperties) {
        return new RoomFanoutInboxSlotValidator(fanoutProperties);
    }

    static final class RoomFanoutInboxSlotValidator {

        RoomFanoutInboxSlotValidator(RoomFanoutProperties fanoutProperties) {
            fanoutProperties.normalizedWorkerInboxSlot();
        }
    }
}
