package com.nowcoder.community.im.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.im.common.command.SendPrivateTextCommandV1;
import com.nowcoder.community.im.common.command.SendRoomTextCommandV1;
import com.nowcoder.community.im.common.event.PrivateMessagePersistedEventV1;
import com.nowcoder.community.im.common.event.PrivateMessageRejectedEventV1;
import com.nowcoder.community.im.common.event.RoomMemberChanged;
import com.nowcoder.community.im.common.event.RoomMemberChangedEventV1;
import com.nowcoder.community.im.common.event.RoomMessagePersistedEventV1;
import com.nowcoder.community.im.common.event.RoomMessageRejectedEventV1;
import com.nowcoder.community.im.common.event.UserBlockRelationChanged;
import com.nowcoder.community.im.common.event.UserMessagingPolicyChanged;
import com.nowcoder.community.im.common.projection.RoomMembershipEntry;
import com.nowcoder.community.im.common.projection.RoomMembershipSnapshot;
import com.nowcoder.community.im.common.projection.UserBlockRelationEntry;
import com.nowcoder.community.im.common.projection.UserBlockRelationSnapshot;
import com.nowcoder.community.im.common.projection.UserMessagingPolicyEntry;
import com.nowcoder.community.im.common.projection.UserMessagingPolicySnapshot;
import com.nowcoder.community.im.common.session.OpenImSessionRequest;
import com.nowcoder.community.im.common.session.OpenImSessionResponse;
import com.nowcoder.community.im.common.ws.AckFrame;
import com.nowcoder.community.im.common.ws.CommittedFrame;
import com.nowcoder.community.im.common.ws.ConnectFrame;
import com.nowcoder.community.im.common.ws.PingFrame;
import com.nowcoder.community.im.common.ws.PongFrame;
import com.nowcoder.community.im.common.ws.PrivateMessageFrame;
import com.nowcoder.community.im.common.ws.RejectFrame;
import com.nowcoder.community.im.common.ws.RoomMessageFrame;
import com.nowcoder.community.im.common.ws.SendPrivateTextFrame;
import com.nowcoder.community.im.common.ws.SendRoomTextFrame;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

        SendPrivateTextCommandV1 back = roundTrip(cmd, SendPrivateTextCommandV1.class);
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

        SendRoomTextCommandV1 back = roundTrip(cmd, SendRoomTextCommandV1.class);
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

        PrivateMessagePersistedEventV1 back = roundTrip(event, PrivateMessagePersistedEventV1.class);
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

        RoomMessagePersistedEventV1 back = roundTrip(event, RoomMessagePersistedEventV1.class);
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

        RoomMemberChangedEventV1 back = roundTrip(event, RoomMemberChangedEventV1.class);
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

        PrivateMessageRejectedEventV1 back = roundTrip(event, PrivateMessageRejectedEventV1.class);
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

        RoomMessageRejectedEventV1 back = roundTrip(event, RoomMessageRejectedEventV1.class);
        assertEquals(event, back);
    }

    @Test
    void shouldRoundTripOpenImSessionRequest() throws Exception {
        OpenImSessionRequest request = new OpenImSessionRequest(Map.of(
                "deviceId", "ios-1",
                "clientVersion", "1.0.0"
        ));

        OpenImSessionRequest back = roundTrip(request, OpenImSessionRequest.class);

        assertEquals("ios-1", back.metadata().get("deviceId"));
        assertEquals("1.0.0", back.metadata().get("clientVersion"));
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

        OpenImSessionResponse back = roundTrip(response, OpenImSessionResponse.class);

        assertEquals("worker-a", back.workerId());
        assertTrue(back.wsUrl().contains("/ws/im/workers/worker-a"));
    }

    @Test
    void shouldRoundTripConnectFrame() throws Exception {
        ConnectFrame frame = new ConnectFrame("connect", "ticket-1");

        ConnectFrame back = roundTrip(frame, ConnectFrame.class);
        assertEquals(frame, back);
    }

    @Test
    void shouldRoundTripSendPrivateTextFrame() throws Exception {
        SendPrivateTextFrame frame = new SendPrivateTextFrame(
                "sendPrivateText",
                "cmsg-6",
                uuid(21),
                "hello private"
        );

        SendPrivateTextFrame back = roundTrip(frame, SendPrivateTextFrame.class);
        assertEquals(frame, back);
    }

    @Test
    void shouldRoundTripSendRoomTextFrame() throws Exception {
        SendRoomTextFrame frame = new SendRoomTextFrame(
                "sendRoomText",
                "cmsg-7",
                uuid(2001),
                "hello room frame"
        );

        SendRoomTextFrame back = roundTrip(frame, SendRoomTextFrame.class);
        assertEquals(frame, back);
    }

    @Test
    void shouldRoundTripAckFrame() throws Exception {
        AckFrame frame = new AckFrame("ack", "sendPrivateText", "cmsg-8", "req-8");

        AckFrame back = roundTrip(frame, AckFrame.class);
        assertEquals(frame, back);
    }

    @Test
    void shouldRoundTripRejectFrame() throws Exception {
        RejectFrame frame = new RejectFrame(
                "reject",
                "sendRoomText",
                "cmsg-9",
                "req-9",
                403,
                "not_room_member",
                "not a room member"
        );

        RejectFrame back = roundTrip(frame, RejectFrame.class);
        assertEquals(frame, back);
    }

    @Test
    void shouldRoundTripCommittedFrame() throws Exception {
        CommittedFrame frame = new CommittedFrame("committed", "sendPrivateText", "cmsg-10", "req-10");

        CommittedFrame back = roundTrip(frame, CommittedFrame.class);
        assertEquals(frame, back);
    }

    @Test
    void shouldRoundTripPrivateMessageFrame() throws Exception {
        PrivateMessageFrame frame = new PrivateMessageFrame(
                "privateMessage",
                conversationId(uuid(31), uuid(32)),
                12L,
                uuid(12001),
                uuid(31),
                uuid(32),
                "persisted hello",
                1_712_345_678_901L
        );

        PrivateMessageFrame back = roundTrip(frame, PrivateMessageFrame.class);
        assertEquals(frame, back);
    }

    @Test
    void shouldRoundTripRoomMessageFrame() throws Exception {
        RoomMessageFrame frame = new RoomMessageFrame(
                "roomMessage",
                uuid(3001),
                14L,
                uuid(14001),
                uuid(41),
                1_712_345_678_902L
        );

        RoomMessageFrame back = roundTrip(frame, RoomMessageFrame.class);
        assertEquals(frame, back);
    }

    @Test
    void shouldRoundTripPingAndPongFrames() throws Exception {
        PingFrame ping = new PingFrame("ping", 1_712_345_678_903L);
        PongFrame pong = new PongFrame("pong", 1_712_345_678_903L);

        PingFrame pingBack = roundTrip(ping, PingFrame.class);
        PongFrame pongBack = roundTrip(pong, PongFrame.class);

        assertEquals(ping, pingBack);
        assertEquals(pong, pongBack);
    }

    @Test
    void shouldRoundTripRoomMembershipEntry() throws Exception {
        RoomMembershipEntry entry = new RoomMembershipEntry(uuid(10), uuid(1));

        RoomMembershipEntry back = roundTrip(entry, RoomMembershipEntry.class);
        assertEquals(entry, back);
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

        RoomMembershipSnapshot back = roundTrip(snapshot, RoomMembershipSnapshot.class);

        assertEquals(1, back.entries().size());
        assertEquals(snapshot.entries().get(0).roomId(), back.entries().get(0).roomId());
        assertEquals(snapshot.entries().get(0).userId(), back.entries().get(0).userId());
        assertFalse(back.hasMore());
    }

    @Test
    void shouldRoundTripUserMessagingPolicySnapshot() throws Exception {
        UserMessagingPolicySnapshot snapshot = new UserMessagingPolicySnapshot(
                List.of(new UserMessagingPolicyEntry(
                        uuid(51),
                        true,
                        false,
                        true,
                        false
                )),
                uuid(52),
                true
        );

        UserMessagingPolicySnapshot back = roundTrip(snapshot, UserMessagingPolicySnapshot.class);

        assertEquals(snapshot, back);
        assertTrue(back.hasMore());
    }

    @Test
    void shouldRoundTripUserBlockRelationSnapshot() throws Exception {
        UserBlockRelationSnapshot snapshot = new UserBlockRelationSnapshot(
                List.of(new UserBlockRelationEntry(
                        uuid(61),
                        uuid(62),
                        true
                )),
                uuid(63),
                uuid(64),
                false
        );

        UserBlockRelationSnapshot back = roundTrip(snapshot, UserBlockRelationSnapshot.class);

        assertEquals(snapshot, back);
        assertFalse(back.hasMore());
    }

    @Test
    void shouldRoundTripRoomMemberChangedEvent() throws Exception {
        RoomMemberChanged event = new RoomMemberChanged(
                "evt-room-member-1",
                uuid(71),
                uuid(72),
                true,
                1_712_345_678_904L
        );

        RoomMemberChanged back = roundTrip(event, RoomMemberChanged.class);
        assertEquals(event, back);
    }

    @Test
    void shouldRoundTripUserMessagingPolicyChanged() throws Exception {
        UserMessagingPolicyChanged event = new UserMessagingPolicyChanged(
                "evt-policy-1",
                uuid(81),
                true,
                false,
                true,
                false,
                1_712_345_678_905L
        );

        UserMessagingPolicyChanged back = roundTrip(event, UserMessagingPolicyChanged.class);
        assertEquals(event, back);
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

        UserBlockRelationChanged back = roundTrip(event, UserBlockRelationChanged.class);

        assertTrue(back.active());
        assertEquals(event.blockerUserId(), back.blockerUserId());
    }

    @Test
    void shouldExposeNewProjectionTopics() {
        assertEquals("im.event.room-member-changed", ImTopics.EVENT_ROOM_MEMBER_CHANGED);
        assertEquals("im.event.user-messaging-policy-changed", ImTopics.EVENT_USER_MESSAGING_POLICY_CHANGED);
        assertEquals("im.event.user-block-relation-changed", ImTopics.EVENT_USER_BLOCK_RELATION_CHANGED);
    }

    private <T> T roundTrip(T value, Class<T> type) throws Exception {
        String json = objectMapper.writeValueAsString(value);
        return objectMapper.readValue(json, type);
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
