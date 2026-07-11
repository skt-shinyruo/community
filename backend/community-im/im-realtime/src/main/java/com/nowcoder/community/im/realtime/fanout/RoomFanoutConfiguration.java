package com.nowcoder.community.im.realtime.fanout;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(RoomFanoutProperties.class)
public class RoomFanoutConfiguration {

    @Bean
    RoomFanoutRoutedInboxSlotGuard roomFanoutRoutedInboxSlotGuard(RoomFanoutProperties fanoutProperties) {
        return new RoomFanoutRoutedInboxSlotGuard(fanoutProperties);
    }

    static final class RoomFanoutRoutedInboxSlotGuard {

        RoomFanoutRoutedInboxSlotGuard(RoomFanoutProperties fanoutProperties) {
            if (fanoutProperties == null || !fanoutProperties.isRoutedMode() || !fanoutProperties.isKafkaTransport()) {
                return;
            }
            fanoutProperties.normalizedWorkerInboxSlot();
        }
    }
}
