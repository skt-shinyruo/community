package com.nowcoder.community.im.realtime.fanout;

import com.nowcoder.community.im.realtime.presence.NoopRoomPresenceDirectory;
import com.nowcoder.community.im.realtime.presence.RoomPresenceDirectory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(RoomFanoutProperties.class)
public class RoomFanoutConfiguration {

    @Bean
    RoomFanoutRoutedPresenceGuard roomFanoutRoutedPresenceGuard(
            RoomFanoutProperties fanoutProperties,
            ObjectProvider<RoomPresenceDirectory> roomPresenceDirectoryProvider
    ) {
        return new RoomFanoutRoutedPresenceGuard(fanoutProperties, roomPresenceDirectoryProvider);
    }

    @Bean
    RoomFanoutRoutedInboxSlotGuard roomFanoutRoutedInboxSlotGuard(RoomFanoutProperties fanoutProperties) {
        return new RoomFanoutRoutedInboxSlotGuard(fanoutProperties);
    }

    static final class RoomFanoutRoutedPresenceGuard {

        RoomFanoutRoutedPresenceGuard(
                RoomFanoutProperties fanoutProperties,
                ObjectProvider<RoomPresenceDirectory> roomPresenceDirectoryProvider
        ) {
            if (fanoutProperties == null || !fanoutProperties.isRoutedMode()) {
                return;
            }
            RoomPresenceDirectory roomPresenceDirectory = roomPresenceDirectoryProvider == null
                    ? null
                    : roomPresenceDirectoryProvider.getIfAvailable();
            if (roomPresenceDirectory == null || roomPresenceDirectory instanceof NoopRoomPresenceDirectory) {
                throw new IllegalStateException(
                        "im.room-fanout.mode=routed requires distributed room presence; "
                                + "enable im.room-presence.enabled with a Redis-backed RoomPresenceDirectory"
                );
            }
        }
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
