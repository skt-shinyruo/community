package com.nowcoder.community.im.realtime.kafka;

import com.nowcoder.community.im.common.event.RoomMemberChanged;
import com.nowcoder.community.im.common.event.UserBlockRelationChanged;
import com.nowcoder.community.im.common.event.UserMessagingPolicyChanged;
import com.nowcoder.community.im.realtime.presence.ConnectionRegistry;
import com.nowcoder.community.im.realtime.presence.RoomLocalPresenceService;
import com.nowcoder.community.im.realtime.projection.MembershipProjectionService;
import com.nowcoder.community.im.realtime.projection.MembershipSnapshotClient;
import com.nowcoder.community.im.realtime.projection.PolicyProjectionService;
import com.nowcoder.community.im.realtime.projection.PolicySnapshotClient;
import com.nowcoder.community.im.realtime.push.PrivatePushService;
import com.nowcoder.community.im.realtime.push.SendResultPushService;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class EventConsumersProjectionEventValidationTest {

    private final MembershipProjectionService membershipProjection = new MembershipProjectionService(
            mock(MembershipSnapshotClient.class));
    private final PolicyProjectionService policyProjection = new PolicyProjectionService(
            mock(PolicySnapshotClient.class));
    private final EventConsumers consumers = new EventConsumers(
            mock(PrivatePushService.class),
            membershipProjection,
            policyProjection,
            mock(ConnectionRegistry.class),
            mock(RoomLocalPresenceService.class),
            mock(SendResultPushService.class)
    );

    @Test
    void nullRoomMemberEventShouldThrowTowardsDlqInsteadOfSilentAck() {
        assertThatThrownBy(() -> consumers.onRoomMemberChanged(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void roomMemberEventWithoutIdentityShouldThrowTowardsDlq() {
        assertThatThrownBy(() -> consumers.onRoomMemberChanged(
                        new RoomMemberChanged("evt-1", null, user(1), "JOINED", 100L, 1L)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> consumers.onRoomMemberChanged(
                        new RoomMemberChanged("evt-2", room(1), null, "JOINED", 100L, 1L)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void roomMemberEventWithUnsupportedActionShouldThrowTowardsDlq() {
        assertThatThrownBy(() -> consumers.onRoomMemberChanged(
                        new RoomMemberChanged("evt-3", room(1), user(1), "BANNED", 100L, 1L)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void malformedPolicyEventShouldThrowTowardsDlq() {
        assertThatThrownBy(() -> consumers.onUserMessagingPolicyChanged(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> consumers.onUserMessagingPolicyChanged(
                        new UserMessagingPolicyChanged(
                                "evt-4", null, true, false, false, null, null, true, 100L, 1L)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void malformedBlockEventShouldThrowTowardsDlq() {
        assertThatThrownBy(() -> consumers.onUserBlockRelationChanged(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> consumers.onUserBlockRelationChanged(
                        new UserBlockRelationChanged("evt-5", null, user(2), true, 100L, 1L)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static UUID room(long suffix) {
        return UUID.fromString("00000000-0000-7000-8000-" + String.format("%012x", suffix));
    }

    private static UUID user(long suffix) {
        return UUID.fromString("00000000-0000-7001-8000-" + String.format("%012x", suffix));
    }
}
