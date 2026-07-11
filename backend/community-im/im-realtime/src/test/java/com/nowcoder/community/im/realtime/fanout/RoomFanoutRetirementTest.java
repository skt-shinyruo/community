package com.nowcoder.community.im.realtime.fanout;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RoomFanoutRetirementTest {

    @Test
    void legacyAndFallbackFanoutClassesAreRetired() {
        assertRetired("com.nowcoder.community.im.realtime.fanout.RoomPersistedLegacyConsumer");
        assertRetired("com.nowcoder.community.im.realtime.fanout.HttpRoomFanoutDispatcher");
        assertRetired("com.nowcoder.community.im.realtime.fanout.RoomFanoutTargetController");
        assertRetired("com.nowcoder.community.im.realtime.fanout.RoomFanoutOwnerCoalescer");
        assertRetired("com.nowcoder.community.im.realtime.presence.NoopRoomPresenceDirectory");
    }

    private static void assertRetired(String className) {
        assertThatThrownBy(() -> Class.forName(className))
                .isInstanceOf(ClassNotFoundException.class);
    }
}
