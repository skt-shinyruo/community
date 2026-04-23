package com.nowcoder.community.im.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.im.common.command.SendPrivateTextCommandV1;
import com.nowcoder.community.im.common.command.SendRoomTextCommandV1;
import com.nowcoder.community.im.common.event.PrivateMessagePersistedEventV1;
import com.nowcoder.community.im.common.event.PrivateMessageRejectedEventV1;
import com.nowcoder.community.im.common.event.RoomMemberChangedEventV1;
import com.nowcoder.community.im.common.event.RoomMessagePersistedEventV1;
import com.nowcoder.community.im.common.event.RoomMessageRejectedEventV1;
import com.nowcoder.community.im.common.event.UserBlockRelationChanged;
import com.nowcoder.community.im.common.projection.RoomMembershipEntry;
import com.nowcoder.community.im.common.projection.RoomMembershipSnapshot;
import com.nowcoder.community.im.common.session.OpenImSessionResponse;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

class JsonContractsTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void command_roundtrip_privateText() throws Exception {
        UUID fromUserId = uuid(12);
        UUID toUserId = uuid(99);
        SendPrivateTextCommandV1 cmd = new SendPrivateTextCommandV1(
                "req-1",
                "cmsg-1",
                fromUserId,
                toUserId,
                conversationId(fromUserId, toUserId),
                "hello",
                1700000000000L
        );

        String json = objectMapper.writeValueAsString(cmd);
        SendPrivateTextCommandV1 back = objectMapper.readValue(json, SendPrivateTextCommandV1.class);
        assertEquals(cmd, back);
    }

    @Test
    void command_roundtrip_roomText() throws Exception {
        UUID fromUserId = uuid(12);
        SendRoomTextCommandV1 cmd = new SendRoomTextCommandV1(
                "req-2",
                "cmsg-2",
                fromUserId,
                uuid(1001),
                "hello room",
                1700000000001L
        );

        String json = objectMapper.writeValueAsString(cmd);
        SendRoomTextCommandV1 back = objectMapper.readValue(json, SendRoomTextCommandV1.class);
        assertEquals(cmd, back);
    }

    @Test
    void event_roundtrip_privatePersisted() throws Exception {
        UUID fromUserId = uuid(12);
        UUID toUserId = uuid(99);
        PrivateMessagePersistedEventV1 event = new PrivateMessagePersistedEventV1(
                "evt-1",
                conversationId(fromUserId, toUserId),
                7L,
                uuid(10001),
                fromUserId,
                toUserId,
                "hello",
                "req-1",
                "cmsg-1",
                1700000001000L
        );

        String json = objectMapper.writeValueAsString(event);
        PrivateMessagePersistedEventV1 back = objectMapper.readValue(json, PrivateMessagePersistedEventV1.class);
        assertEquals(event, back);
    }

    @Test
    void event_roundtrip_roomPersisted() throws Exception {
        UUID fromUserId = uuid(12);
        RoomMessagePersistedEventV1 event = new RoomMessagePersistedEventV1(
                "evt-2",
                uuid(1001),
                7L,
                uuid(20001),
                fromUserId,
                "req-2",
                "cmsg-2",
                1700000002000L
        );

        String json = objectMapper.writeValueAsString(event);
        RoomMessagePersistedEventV1 back = objectMapper.readValue(json, RoomMessagePersistedEventV1.class);
        assertEquals(event, back);
    }

    @Test
    void event_roundtrip_roomMemberChanged() throws Exception {
        RoomMemberChangedEventV1 event = new RoomMemberChangedEventV1(
                "evt-3",
                uuid(1001),
                uuid(12),
                "JOINED",
                1700000003000L
        );

        String json = objectMapper.writeValueAsString(event);
        RoomMemberChangedEventV1 back = objectMapper.readValue(json, RoomMemberChangedEventV1.class);
        assertEquals(event, back);
    }

    @Test
    void event_roundtrip_privateRejected() throws Exception {
        UUID fromUserId = uuid(12);
        UUID toUserId = uuid(99);
        PrivateMessageRejectedEventV1 event = new PrivateMessageRejectedEventV1(
                "evt-4",
                "req-4",
                "cmsg-4",
                fromUserId,
                toUserId,
                conversationId(fromUserId, toUserId),
                403,
                "send_denied",
                "send denied",
                1700000004000L
        );

        String json = objectMapper.writeValueAsString(event);
        PrivateMessageRejectedEventV1 back = objectMapper.readValue(json, PrivateMessageRejectedEventV1.class);
        assertEquals(event, back);
    }

    @Test
    void event_roundtrip_roomRejected() throws Exception {
        RoomMessageRejectedEventV1 event = new RoomMessageRejectedEventV1(
                "evt-5",
                "req-5",
                "cmsg-5",
                uuid(12),
                uuid(1001),
                403,
                "not_room_member",
                "not a room member",
                1700000005000L
        );

        String json = objectMapper.writeValueAsString(event);
        RoomMessageRejectedEventV1 back = objectMapper.readValue(json, RoomMessageRejectedEventV1.class);
        assertEquals(event, back);
    }

    @Test
    void shouldRoundTripOpenImSessionResponse() throws Exception {
        OpenImSessionResponse response = new OpenImSessionResponse(
                "sess-1",
                "worker-a",
                "wss://community.example/ws/im/workers/worker-a",
                "ticket-1",
                1_712_345_678_901L
        );

        String json = objectMapper.writeValueAsString(response);
        OpenImSessionResponse back = objectMapper.readValue(json, OpenImSessionResponse.class);

        assertThat(back.workerId()).isEqualTo("worker-a");
        assertThat(back.wsUrl()).contains("/ws/im/workers/worker-a");
    }

    @Test
    void shouldRoundTripRoomMembershipSnapshot() throws Exception {
        RoomMembershipSnapshot snapshot = new RoomMembershipSnapshot(
                List.of(new RoomMembershipEntry(
                        UUID.fromString("00000000-0000-7000-8000-000000000010"),
                        UUID.fromString("00000000-0000-7000-8000-000000000001")
                )),
                UUID.fromString("00000000-0000-7000-8000-000000000010"),
                UUID.fromString("00000000-0000-7000-8000-000000000001"),
                false
        );

        String json = objectMapper.writeValueAsString(snapshot);
        RoomMembershipSnapshot back = objectMapper.readValue(json, RoomMembershipSnapshot.class);

        assertThat(back.entries()).hasSize(1);
        assertThat(back.entries().get(0).roomId()).isEqualTo(snapshot.entries().get(0).roomId());
    }

    @Test
    void shouldRoundTripUserBlockRelationChanged() throws Exception {
        UserBlockRelationChanged event = new UserBlockRelationChanged(
                "evt-block-1",
                UUID.fromString("00000000-0000-7000-8000-000000000011"),
                UUID.fromString("00000000-0000-7000-8000-000000000022"),
                true,
                1_712_345_678_901L
        );

        String json = objectMapper.writeValueAsString(event);
        UserBlockRelationChanged back = objectMapper.readValue(json, UserBlockRelationChanged.class);

        assertThat(back.active()).isTrue();
        assertThat(back.blockerUserId()).isEqualTo(event.blockerUserId());
    }

    @Test
    void shouldExposeNewProjectionTopics() {
        assertThat(ImTopics.EVENT_ROOM_MEMBER_CHANGED)
                .isEqualTo("im.event.room-member-changed");
        assertThat(ImTopics.EVENT_USER_MESSAGING_POLICY_CHANGED)
                .isEqualTo("im.event.user-messaging-policy-changed");
        assertThat(ImTopics.EVENT_USER_BLOCK_RELATION_CHANGED)
                .isEqualTo("im.event.user-block-relation-changed");
    }

    private static UUID uuid(long suffix) {
        return UUID.fromString("00000000-0000-7000-8000-" + String.format("%012x", suffix));
    }

    private static String conversationId(UUID userId1, UUID userId2) {
        UUID first = userId1.compareTo(userId2) <= 0 ? userId1 : userId2;
        UUID second = first.equals(userId1) ? userId2 : userId1;
        return first + "_" + second;
    }
}
