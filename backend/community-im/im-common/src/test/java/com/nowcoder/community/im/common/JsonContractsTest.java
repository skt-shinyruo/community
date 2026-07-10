package com.nowcoder.community.im.common;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nowcoder.community.im.common.command.RoomFanoutCommand;
import com.nowcoder.community.im.common.command.SendPrivateTextCommand;
import com.nowcoder.community.im.common.command.SendRoomTextCommand;
import com.nowcoder.community.im.common.event.PrivateMessageCommittedEvent;
import com.nowcoder.community.im.common.event.PrivateMessagePersistedEvent;
import com.nowcoder.community.im.common.event.PrivateMessageRejectedEvent;
import com.nowcoder.community.im.common.event.RoomMemberChanged;
import com.nowcoder.community.im.common.event.RoomMessageCommittedEvent;
import com.nowcoder.community.im.common.event.RoomMessagePersistedEvent;
import com.nowcoder.community.im.common.event.RoomMessageRejectedEvent;
import com.nowcoder.community.im.common.event.UserBlockRelationChanged;
import com.nowcoder.community.im.common.event.UserMessagingPolicyChanged;
import com.nowcoder.community.im.common.policy.PrivateMessagePolicyDecision;
import com.nowcoder.community.im.common.projection.RoomMembershipEntry;
import com.nowcoder.community.im.common.projection.RoomMembershipSnapshot;
import com.nowcoder.community.im.common.projection.UserBlockRelationEntry;
import com.nowcoder.community.im.common.projection.UserBlockRelationSnapshot;
import com.nowcoder.community.im.common.projection.UserMessagingPolicyEntry;
import com.nowcoder.community.im.common.projection.UserMessagingPolicySnapshot;
import com.nowcoder.community.im.common.session.OpenImSessionResponse;
import com.nowcoder.community.im.common.ws.AckFrame;
import com.nowcoder.community.im.common.ws.CommittedFrame;
import com.nowcoder.community.im.common.ws.ConnectFrame;
import com.nowcoder.community.im.common.ws.ConnectedFrame;
import com.nowcoder.community.im.common.ws.PingFrame;
import com.nowcoder.community.im.common.ws.PongFrame;
import com.nowcoder.community.im.common.ws.PrivateMessageFrame;
import com.nowcoder.community.im.common.ws.RejectFrame;
import com.nowcoder.community.im.common.ws.RoomMessageFrame;
import com.nowcoder.community.im.common.ws.SendPrivateTextFrame;
import com.nowcoder.community.im.common.ws.SendRoomTextFrame;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.RecordComponent;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonContractsTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void command_roundtrip_privateText() throws Exception {
        UUID fromUserId = uuid(12);
        UUID toUserId = uuid(99);
        SendPrivateTextCommand cmd = new SendPrivateTextCommand(
                "req-1",
                "cmsg-1",
                fromUserId,
                toUserId,
                conversationId(fromUserId, toUserId),
                "hello",
                1700000000000L
        );

        SendPrivateTextCommand back = roundTrip(cmd, SendPrivateTextCommand.class);
        assertEquals(cmd, back);
        assertEquals(1, recordComponentValue(back, "schemaVersion"));
    }

    @Test
    void command_roundtrip_roomText() throws Exception {
        UUID fromUserId = uuid(12);
        SendRoomTextCommand cmd = new SendRoomTextCommand(
                "req-2",
                "cmsg-2",
                fromUserId,
                uuid(1001),
                "hello room",
                1700000000001L
        );

        SendRoomTextCommand back = roundTrip(cmd, SendRoomTextCommand.class);
        assertEquals(cmd, back);
        assertEquals(1, recordComponentValue(back, "schemaVersion"));
    }

    @Test
    void command_roundtrip_roomFanout() throws Exception {
        RoomFanoutCommand cmd = new RoomFanoutCommand(
                "worker-a",
                uuid(1001),
                42L,
                "evt-1",
                1700000000001L
        );

        RoomFanoutCommand back = roundTrip(cmd, RoomFanoutCommand.class);

        assertEquals(cmd, back);
        assertEquals(1, recordComponentValue(back, "schemaVersion"));
    }

    @Test
    void command_shouldIgnoreFutureFieldsAndKeepRoomTextFieldsReadable() throws Exception {
        SendRoomTextCommand back = objectMapper.readValue("""
                {
                  "schemaVersion": 1,
                  "requestId": "req-future-room",
                  "clientMsgId": "cmsg-future-room",
                  "fromUserId": "00000000-0000-7000-8000-00000000000c",
                  "roomId": "00000000-0000-7000-8000-0000000003e9",
                  "content": "future room hello",
                  "clientSentAtEpochMs": 1700000000001,
                  "traceContext": "future writer metadata",
                  "futureObject": {"feature": "attachment-preview"}
                }
                """, SendRoomTextCommand.class);

        assertEquals("req-future-room", back.requestId());
        assertEquals("cmsg-future-room", back.clientMsgId());
        assertEquals(uuid(1001), back.roomId());
        assertEquals("future room hello", back.content());
        assertEquals(1, recordComponentValue(back, "schemaVersion"));
    }

    @Test
    void event_roundtrip_privatePersisted() throws Exception {
        UUID fromUserId = uuid(12);
        UUID toUserId = uuid(99);
        PrivateMessagePersistedEvent event = new PrivateMessagePersistedEvent(
                "evt-1",
                conversationId(fromUserId, toUserId),
                7L,
                uuid(10001),
                fromUserId,
                toUserId,
                "hello",
                1700000001000L
        );

        PrivateMessagePersistedEvent back = roundTrip(event, PrivateMessagePersistedEvent.class);
        assertEquals(event, back);
        assertEquals(1, recordComponentValue(back, "schemaVersion"));
    }

    @Test
    void event_roundtrip_roomPersisted() throws Exception {
        UUID fromUserId = uuid(12);
        RoomMessagePersistedEvent event = new RoomMessagePersistedEvent(
                "evt-2",
                uuid(1001),
                7L,
                uuid(20001),
                fromUserId,
                1700000002000L
        );

        RoomMessagePersistedEvent back = roundTrip(event, RoomMessagePersistedEvent.class);
        assertEquals(event, back);
        assertEquals(1, recordComponentValue(back, "schemaVersion"));
    }

    @Test
    void event_roundtrip_privateCommitted() throws Exception {
        UUID fromUserId = uuid(12);
        UUID toUserId = uuid(99);
        PrivateMessageCommittedEvent event = new PrivateMessageCommittedEvent(
                "evt-committed-1",
                "req-1",
                "cmsg-1",
                fromUserId,
                toUserId,
                conversationId(fromUserId, toUserId),
                uuid(10001),
                7L,
                1700000001000L
        );

        PrivateMessageCommittedEvent back = roundTrip(event, PrivateMessageCommittedEvent.class);
        assertEquals(event, back);
        assertEquals(1, recordComponentValue(back, "schemaVersion"));
    }

    @Test
    void event_roundtrip_roomCommitted() throws Exception {
        UUID fromUserId = uuid(12);
        RoomMessageCommittedEvent event = new RoomMessageCommittedEvent(
                "evt-committed-2",
                "req-2",
                "cmsg-2",
                fromUserId,
                uuid(1001),
                uuid(20001),
                7L,
                1700000002000L
        );

        RoomMessageCommittedEvent back = roundTrip(event, RoomMessageCommittedEvent.class);
        assertEquals(event, back);
        assertEquals(1, recordComponentValue(back, "schemaVersion"));
    }

    @Test
    void event_roundtrip_privateRejected() throws Exception {
        UUID fromUserId = uuid(12);
        UUID toUserId = uuid(99);
        PrivateMessageRejectedEvent event = new PrivateMessageRejectedEvent(
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

        PrivateMessageRejectedEvent back = roundTrip(event, PrivateMessageRejectedEvent.class);
        assertEquals(event, back);
        assertEquals(1, recordComponentValue(back, "schemaVersion"));
    }

    @Test
    void event_roundtrip_roomRejected() throws Exception {
        RoomMessageRejectedEvent event = new RoomMessageRejectedEvent(
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

        RoomMessageRejectedEvent back = roundTrip(event, RoomMessageRejectedEvent.class);
        assertEquals(event, back);
        assertEquals(1, recordComponentValue(back, "schemaVersion"));
    }

    @Test
    void event_shouldIgnoreFutureFieldsAndKeepPersistedEventFieldsReadable() throws Exception {
        RoomMessagePersistedEvent back = objectMapper.readValue("""
                {
                  "schemaVersion": 1,
                  "eventId": "evt-future-room-persisted",
                  "roomId": "00000000-0000-7000-8000-0000000003e9",
                  "seq": 11,
                  "messageId": "00000000-0000-7000-8000-000000004e21",
                  "fromUserId": "00000000-0000-7000-8000-00000000000c",
                  "createdAtEpochMs": 1700000002000,
                  "deliveryHint": "future-writer-added-field"
                }
                """, RoomMessagePersistedEvent.class);

        assertEquals("evt-future-room-persisted", back.eventId());
        assertEquals(uuid(1001), back.roomId());
        assertEquals(11L, back.seq());
        assertEquals(uuid(20001), back.messageId());
        assertEquals(1, recordComponentValue(back, "schemaVersion"));
    }

    @Test
    void shouldRoundTripOpenImSessionResponse() throws Exception {
        OpenImSessionResponse response = new OpenImSessionResponse(
                "sess-1",
                "wss://community.example/ws/im",
                "ticket-1",
                1_712_345_678_901L
        );

        OpenImSessionResponse back = roundTrip(response, OpenImSessionResponse.class);

        assertEquals("sess-1", back.sessionId());
        assertEquals("wss://community.example/ws/im", back.wsUrl());
        assertEquals("ticket-1", back.ticket());
        assertEquals(1_712_345_678_901L, back.expiresAtEpochMillis());
    }

    @Test
    void shouldRoundTripConnectFrame() throws Exception {
        ConnectFrame frame = new ConnectFrame("connect", "ticket-1");

        ConnectFrame back = roundTrip(frame, ConnectFrame.class);
        assertEquals(frame, back);
        assertEquals(1, recordComponentValue(back, "schemaVersion"));
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
        assertEquals(1, recordComponentValue(back, "schemaVersion"));
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
        assertEquals(1, recordComponentValue(back, "schemaVersion"));
    }

    @Test
    void shouldRoundTripAckFrame() throws Exception {
        AckFrame frame = new AckFrame("ack", "sendPrivateText", "cmsg-8", "req-8");

        AckFrame back = roundTrip(frame, AckFrame.class);
        assertEquals(frame, back);
        assertEquals(1, recordComponentValue(back, "schemaVersion"));
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
        assertEquals(1, recordComponentValue(back, "schemaVersion"));
    }

    @Test
    void shouldRoundTripCommittedFrame() throws Exception {
        CommittedFrame frame = new CommittedFrame(
                "committed",
                "sendPrivateText",
                "cmsg-10",
                "req-10",
                conversationId(uuid(31), uuid(32)),
                null,
                uuid(10001),
                18L
        );

        CommittedFrame back = roundTrip(frame, CommittedFrame.class);
        assertEquals(frame, back);
        assertEquals(1, recordComponentValue(back, "schemaVersion"));
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
        assertEquals(1, recordComponentValue(back, "schemaVersion"));
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
        assertEquals(1, recordComponentValue(back, "schemaVersion"));
    }

    @Test
    void shouldRoundTripPingAndPongFrames() throws Exception {
        PingFrame ping = new PingFrame("ping", 1_712_345_678_903L);
        PongFrame pong = new PongFrame("pong", 1_712_345_678_903L);

        PingFrame pingBack = roundTrip(ping, PingFrame.class);
        PongFrame pongBack = roundTrip(pong, PongFrame.class);

        assertEquals(ping, pingBack);
        assertEquals(pong, pongBack);
        assertEquals(1, recordComponentValue(pingBack, "schemaVersion"));
        assertEquals(1, recordComponentValue(pongBack, "schemaVersion"));
    }

    @Test
    void frame_shouldIgnoreFutureFieldsAndKeepCommittedFieldsReadable() throws Exception {
        CommittedFrame back = objectMapper.readValue("""
                {
                  "schemaVersion": 1,
                  "type": "committed",
                  "cmd": "sendPrivateText",
                  "clientMsgId": "cmsg-future-frame",
                  "requestId": "req-future-frame",
                  "conversationId": "%s",
                  "roomId": null,
                  "messageId": "00000000-0000-7000-8000-000000002711",
                  "seq": 18,
                  "serverTraceId": "future-outbound-metadata"
                }
                """.formatted(conversationId(uuid(31), uuid(32))), CommittedFrame.class);

        assertEquals("committed", back.type());
        assertEquals("sendPrivateText", back.cmd());
        assertEquals("cmsg-future-frame", back.clientMsgId());
        assertEquals("req-future-frame", back.requestId());
        assertEquals(uuid(10001), back.messageId());
        assertEquals(18L, back.seq());
        assertEquals(1, recordComponentValue(back, "schemaVersion"));
    }

    @Test
    void shouldRoundTripRoomMembershipEntry() throws Exception {
        RoomMembershipEntry entry = new RoomMembershipEntry(uuid(10), uuid(1), 10L, null);

        RoomMembershipEntry back = roundTrip(entry, RoomMembershipEntry.class);
        assertEquals(entry, back);
        assertEquals(1, recordComponentValue(back, "schemaVersion"));
    }

    @Test
    void shouldRoundTripRoomMembershipSnapshot() throws Exception {
        RoomMembershipSnapshot snapshot = new RoomMembershipSnapshot(
                List.of(new RoomMembershipEntry(
                        UUID.fromString("00000000-0000-7000-8000-000000000010"),
                        UUID.fromString("00000000-0000-7000-8000-000000000001"),
                        1_712_345_678_904L,
                        null
                )),
                UUID.fromString("00000000-0000-7000-8000-000000000010"),
                UUID.fromString("00000000-0000-7000-8000-000000000001"),
                false,
                1_712_345_678_904L
        );

        RoomMembershipSnapshot back = roundTrip(snapshot, RoomMembershipSnapshot.class);

        assertEquals(1, back.entries().size());
        assertEquals(snapshot.entries().get(0).roomId(), back.entries().get(0).roomId());
        assertEquals(snapshot.entries().get(0).userId(), back.entries().get(0).userId());
        assertEquals(snapshot.nextRoomId(), back.nextRoomId());
        assertEquals(snapshot.nextUserId(), back.nextUserId());
        assertFalse(back.hasMore());
        assertEquals(1_712_345_678_904L, back.snapshotHighWatermark());
        assertEquals(1, recordComponentValue(back, "schemaVersion"));
        assertEquals(1, recordComponentValue(back.entries().get(0), "schemaVersion"));
    }

    @Test
    void shouldRoundTripUserMessagingPolicySnapshot() throws Exception {
        UserMessagingPolicySnapshot snapshot = new UserMessagingPolicySnapshot(
                List.of(newRecord(UserMessagingPolicyEntry.class, userMessagingPolicyEntryValues(
                        uuid(51),
                        true,
                        false,
                        true,
                        1_712_345_678_906L,
                        null,
                        false,
                        1_712_345_678_901L,
                        1_712_345_678_900L
                ))),
                uuid(52),
                true,
                1_712_345_678_901L
        );

        UserMessagingPolicySnapshot back = roundTrip(snapshot, UserMessagingPolicySnapshot.class);

        assertEquals(snapshot, back);
        assertEquals(1_712_345_678_906L, recordComponentValue(back.entries().get(0), "muteUntil"));
        assertEquals(null, recordComponentValue(back.entries().get(0), "banUntil"));
        assertEquals(1_712_345_678_901L, recordComponentValue(back.entries().get(0), "version"));
        assertEquals(1_712_345_678_900L, recordComponentValue(back.entries().get(0), "occurredAtEpochMillis"));
        assertTrue(back.hasMore());
        assertEquals(1_712_345_678_901L, back.snapshotHighWatermark());
        assertEquals(1, recordComponentValue(back, "schemaVersion"));
        assertEquals(1, recordComponentValue(back.entries().get(0), "schemaVersion"));
    }

    @Test
    void shouldRoundTripUserBlockRelationSnapshot() throws Exception {
        UserBlockRelationSnapshot snapshot = new UserBlockRelationSnapshot(
                List.of(new UserBlockRelationEntry(
                        uuid(61),
                        uuid(62),
                        true,
                        1_712_345_678_902L,
                        1_712_345_678_901L
                )),
                uuid(63),
                uuid(64),
                false,
                1_712_345_678_902L
        );

        UserBlockRelationSnapshot back = roundTrip(snapshot, UserBlockRelationSnapshot.class);

        assertEquals(snapshot, back);
        assertFalse(back.hasMore());
        assertEquals(1_712_345_678_902L, back.snapshotHighWatermark());
        assertEquals(1, recordComponentValue(back, "schemaVersion"));
        assertEquals(1, recordComponentValue(back.entries().get(0), "schemaVersion"));
    }

    @Test
    void projection_shouldIgnoreFutureFieldsAndKeepSnapshotFieldsReadable() throws Exception {
        RoomMembershipSnapshot back = objectMapper.readValue("""
                {
                  "schemaVersion": 1,
                  "entries": [
                    {
                      "schemaVersion": 1,
                      "roomId": "00000000-0000-7000-8000-00000000000a",
                      "userId": "00000000-0000-7000-8000-000000000001",
                      "version": 1712345678904,
                      "occurredAtEpochMillis": 1712345678903,
                      "role": "future-admin"
                    }
                  ],
                  "nextRoomId": "00000000-0000-7000-8000-00000000000a",
                  "nextUserId": "00000000-0000-7000-8000-000000000001",
                  "hasMore": false,
                  "snapshotHighWatermark": 1712345678904,
                  "futureCursor": "opaque"
                }
                """, RoomMembershipSnapshot.class);

        assertEquals(1, back.entries().size());
        assertEquals(uuid(10), back.entries().get(0).roomId());
        assertEquals(uuid(1), back.entries().get(0).userId());
        assertFalse(back.hasMore());
        assertEquals(1_712_345_678_904L, back.snapshotHighWatermark());
        assertEquals(1, recordComponentValue(back, "schemaVersion"));
        assertEquals(1, recordComponentValue(back.entries().get(0), "schemaVersion"));
    }

    @Test
    void projectionDeltasShouldRequirePositiveVersion() throws Exception {
        for (Class<?> contractType : List.of(
                RoomMemberChanged.class,
                UserMessagingPolicyChanged.class,
                UserBlockRelationChanged.class
        )) {
            assertPositiveVersionRequired(contractType);
        }
    }

    @Test
    void projectionEntriesShouldRequirePositiveVersion() throws Exception {
        for (Class<?> contractType : List.of(
                RoomMembershipEntry.class,
                UserMessagingPolicyEntry.class,
                UserBlockRelationEntry.class
        )) {
            assertPositiveVersionRequired(contractType);
        }
    }

    @Test
    void projectionSnapshotsShouldRequireNonNegativeWatermark() throws Exception {
        for (Class<?> contractType : List.of(
                RoomMembershipSnapshot.class,
                UserMessagingPolicySnapshot.class,
                UserBlockRelationSnapshot.class
        )) {
            String validJson = genericValidJson(contractType);

            assertJsonRejected(withoutField(validJson, "snapshotHighWatermark"), contractType);
            assertJsonRejected(withField(validJson, "snapshotHighWatermark", null), contractType);
            assertJsonRejected(withField(validJson, "snapshotHighWatermark", -1), contractType);
        }
    }

    @Test
    void emptyProjectionSnapshotShouldAllowZeroWatermark() throws Exception {
        RoomMembershipSnapshot empty = objectMapper.readValue("""
                {"schemaVersion":1,"entries":[],"nextRoomId":null,"nextUserId":null,
                 "hasMore":false,"snapshotHighWatermark":0}
                """, RoomMembershipSnapshot.class);
        assertEquals(0L, empty.snapshotHighWatermark());
    }

    @Test
    void shouldRoundTripPrivateMessagePolicyDecision() throws Exception {
        PrivateMessagePolicyDecision decision = new PrivateMessagePolicyDecision(
                false,
                403,
                "policy_denied",
                "用户已拉黑",
                1_712_345_678_910L
        );

        PrivateMessagePolicyDecision back = roundTrip(decision, PrivateMessagePolicyDecision.class);

        assertEquals(decision, back);
        assertFalse(back.allowed());
    }

    @Test
    void shouldRoundTripRoomMemberChangedEvent() throws Exception {
        RoomMemberChanged event = new RoomMemberChanged(
                "evt-room-member-1",
                uuid(71),
                uuid(72),
                "JOINED",
                1_712_345_678_904L,
                1_712_345_678_905L
        );

        RoomMemberChanged back = roundTrip(event, RoomMemberChanged.class);
        assertEquals(event, back);
        assertEquals("JOINED", back.action());
        assertEquals(1_712_345_678_905L, back.version());
    }

    @Test
    void shouldRoundTripUserMessagingPolicyChanged() throws Exception {
        UserMessagingPolicyChanged event = newRecord(UserMessagingPolicyChanged.class, userMessagingPolicyChangedValues(
                "evt-policy-1",
                uuid(81),
                true,
                false,
                true,
                1_712_345_678_907L,
                null,
                false,
                1_712_345_678_905L,
                1_712_345_678_906L
        ));

        UserMessagingPolicyChanged back = roundTrip(event, UserMessagingPolicyChanged.class);
        assertEquals(event, back);
        assertEquals(1_712_345_678_907L, recordComponentValue(back, "muteUntil"));
        assertEquals(null, recordComponentValue(back, "banUntil"));
        assertEquals(1_712_345_678_906L, recordComponentValue(back, "version"));
    }

    @Test
    void shouldIgnoreUnknownUserMessagingPolicyFieldNames() throws Exception {
        UserMessagingPolicyChanged back = objectMapper.readValue("""
                {
                  "schemaVersion": 1,
                  "eventId": "evt-policy-unknown",
                  "userId": "00000000-0000-7000-8000-000000000081",
                  "userExists": true,
                  "suspended": false,
                  "muted": false,
                  "unknownPolicyFlag": true,
                  "occurredAtEpochMillis": 1712345678905,
                  "version": 1712345678906
                }
                """, UserMessagingPolicyChanged.class);

        assertEquals("evt-policy-unknown", back.eventId());
        assertEquals(UUID.fromString("00000000-0000-7000-8000-000000000081"), back.userId());
        assertFalse(back.canSendPrivate());
        assertEquals(1, recordComponentValue(back, "schemaVersion"));
    }

    @Test
    void shouldRoundTripUserBlockRelationChanged() throws Exception {
        UserBlockRelationChanged event = new UserBlockRelationChanged(
                "evt-block-1",
                UUID.fromString("00000000-0000-7000-8000-000000000011"),
                UUID.fromString("00000000-0000-7000-8000-000000000022"),
                true,
                1_712_345_678_901L,
                1_712_345_678_902L
        );

        UserBlockRelationChanged back = roundTrip(event, UserBlockRelationChanged.class);

        assertEquals("evt-block-1", back.eventId());
        assertTrue(back.active());
        assertEquals(event.blockerUserId(), back.blockerUserId());
        assertEquals(event.blockedUserId(), back.blockedUserId());
        assertEquals(1_712_345_678_901L, back.occurredAtEpochMillis());
        assertEquals(1_712_345_678_902L, back.version());
        assertEquals(1, recordComponentValue(back, "schemaVersion"));
    }

    @Test
    void shouldExposeNewProjectionTopics() {
        assertEquals("im.event.private-committed", ImTopics.EVENT_PRIVATE_COMMITTED);
        assertEquals("im.event.room-committed", ImTopics.EVENT_ROOM_COMMITTED);
        assertEquals("im.event.room-member-changed", ImTopics.EVENT_ROOM_MEMBER_CHANGED);
        assertEquals("im.event.user-messaging-policy-changed", ImTopics.EVENT_USER_MESSAGING_POLICY_CHANGED);
        assertEquals("im.event.user-block-relation-changed", ImTopics.EVENT_USER_BLOCK_RELATION_CHANGED);
        assertEquals("im.command.room-fanout-routed", ImTopics.COMMAND_ROOM_FANOUT_ROUTED);
    }

    @Test
    void shouldExposeContractVersionConstants() throws Exception {
        assertEquals(1, classConstant("com.nowcoder.community.im.common.ImContractVersions", "CURRENT_SCHEMA_VERSION"));
        assertEquals(1, classConstant("com.nowcoder.community.im.common.ImContractVersions", "WS_FRAME_VERSION"));
        assertEquals("im.event.private-committed", ImTopics.EVENT_PRIVATE_COMMITTED);
    }

    @Test
    void shouldRequireExplicitV1SchemaVersionForEveryVersionedRecord() throws Exception {
        for (Class<?> contractType : List.of(
                RoomFanoutCommand.class,
                SendPrivateTextCommand.class,
                SendRoomTextCommand.class,
                PrivateMessagePersistedEvent.class,
                RoomMessagePersistedEvent.class,
                PrivateMessageCommittedEvent.class,
                RoomMessageCommittedEvent.class,
                PrivateMessageRejectedEvent.class,
                RoomMessageRejectedEvent.class,
                RoomMemberChanged.class,
                UserMessagingPolicyChanged.class,
                UserBlockRelationChanged.class,
                RoomMembershipSnapshot.class,
                RoomMembershipEntry.class,
                UserMessagingPolicySnapshot.class,
                UserMessagingPolicyEntry.class,
                UserBlockRelationSnapshot.class,
                UserBlockRelationEntry.class,
                ConnectFrame.class,
                ConnectedFrame.class,
                SendPrivateTextFrame.class,
                SendRoomTextFrame.class,
                AckFrame.class,
                RejectFrame.class,
                CommittedFrame.class,
                PrivateMessageFrame.class,
                RoomMessageFrame.class,
                PingFrame.class,
                PongFrame.class
        )) {
            String validJson = genericValidJson(contractType);

            assertSchemaRejected(withoutField(validJson, "schemaVersion"), contractType);
            assertSchemaRejected(withField(validJson, "schemaVersion", null), contractType);
            assertSchemaRejected(withField(validJson, "schemaVersion", 0), contractType);
            assertSchemaRejected(withField(validJson, "schemaVersion", -1), contractType);
            assertSchemaRejected(withField(validJson, "schemaVersion", 2), contractType);
            assertSchemaRejected(withField(validJson, "schemaVersion", "1"), contractType);

            Object fixture = objectMapper.readValue(validJson, contractType);
            String json = objectMapper.writeValueAsString(fixture);
            JsonNode serialized = objectMapper.readTree(json);
            assertTrue(serialized.has("schemaVersion"),
                    contractType.getSimpleName() + " must serialize schemaVersion");
            assertEquals(1, serialized.get("schemaVersion").intValue(), contractType.getSimpleName());
        }
    }

    private <T> T roundTrip(T value, Class<T> type) throws Exception {
        String json = objectMapper.writeValueAsString(value);
        return objectMapper.readValue(json, type);
    }

    private <T> void assertSchemaRejected(String json, Class<T> type) {
        assertThrows(
                JsonMappingException.class,
                () -> objectMapper.readValue(json, type),
                () -> type.getSimpleName() + " accepted invalid schema JSON: " + json
        );
    }

    private void assertPositiveVersionRequired(Class<?> contractType) throws Exception {
        String validJson = genericValidJson(contractType);

        assertJsonRejected(withoutField(validJson, "version"), contractType);
        assertJsonRejected(withField(validJson, "version", null), contractType);
        assertJsonRejected(withField(validJson, "version", 0), contractType);
        assertJsonRejected(withField(validJson, "version", -1), contractType);
    }

    private <T> void assertJsonRejected(String json, Class<T> type) {
        assertThrows(
                JsonMappingException.class,
                () -> objectMapper.readValue(json, type),
                () -> type.getSimpleName() + " accepted invalid contract JSON: " + json
        );
    }

    private String withoutField(String json, String fieldName) throws Exception {
        ObjectNode root = (ObjectNode) objectMapper.readTree(json);
        root.remove(fieldName);
        return objectMapper.writeValueAsString(root);
    }

    private String withField(String json, String fieldName, Object value) throws Exception {
        ObjectNode root = (ObjectNode) objectMapper.readTree(json);
        root.set(fieldName, objectMapper.valueToTree(value));
        return objectMapper.writeValueAsString(root);
    }

    private String genericValidJson(Class<?> recordType) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (RecordComponent component : recordType.getRecordComponents()) {
            values.put(component.getName(), genericJsonValue(recordType, component));
        }
        try {
            return objectMapper.writeValueAsString(values);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to build test JSON for " + recordType.getSimpleName(), e);
        }
    }

    private static Object genericJsonValue(Class<?> recordType, RecordComponent component) {
        if ("schemaVersion".equals(component.getName())) {
            return ImContractVersions.CURRENT_SCHEMA_VERSION;
        }
        if ("type".equals(component.getName())) {
            return expectedFrameType(recordType);
        }
        Class<?> type = component.getType();
        if (type == String.class) {
            return component.getName() + "-value";
        }
        if (type == UUID.class) {
            return uuid(1);
        }
        if (type == int.class || type == Integer.class) {
            return 400;
        }
        if (type == long.class || type == Long.class) {
            return 1L;
        }
        if (type == boolean.class || type == Boolean.class) {
            return true;
        }
        if (type == List.class) {
            return List.of();
        }
        throw new AssertionError("Unsupported test JSON component type: "
                + recordType.getSimpleName() + "." + component.getName() + " " + type.getName());
    }

    private static String expectedFrameType(Class<?> recordType) {
        if (recordType == AckFrame.class) {
            return "ack";
        }
        if (recordType == CommittedFrame.class) {
            return "committed";
        }
        if (recordType == ConnectFrame.class) {
            return "connect";
        }
        if (recordType == ConnectedFrame.class) {
            return "connected";
        }
        if (recordType == PingFrame.class) {
            return "ping";
        }
        if (recordType == PongFrame.class) {
            return "pong";
        }
        if (recordType == PrivateMessageFrame.class) {
            return "privateMessage";
        }
        if (recordType == RejectFrame.class) {
            return "reject";
        }
        if (recordType == RoomMessageFrame.class) {
            return "roomMessage";
        }
        if (recordType == SendPrivateTextFrame.class) {
            return "sendPrivateText";
        }
        if (recordType == SendRoomTextFrame.class) {
            return "sendRoomText";
        }
        throw new AssertionError("No frame type test value for " + recordType.getSimpleName());
    }

    private static Map<String, Object> userMessagingPolicyEntryValues(
            UUID userId,
            boolean userExists,
            boolean suspended,
            boolean muted,
            Long muteUntil,
            Long banUntil,
            boolean canSendPrivate,
            Long version,
            Long occurredAtEpochMillis
    ) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("userId", userId);
        values.put("userExists", userExists);
        values.put("suspended", suspended);
        values.put("muted", muted);
        values.put("muteUntil", muteUntil);
        values.put("banUntil", banUntil);
        values.put("canSendPrivate", canSendPrivate);
        values.put("version", version);
        values.put("occurredAtEpochMillis", occurredAtEpochMillis);
        values.put("schemaVersion", ImContractVersions.PROJECTION_SCHEMA_VERSION);
        return values;
    }

    private static Map<String, Object> userMessagingPolicyChangedValues(
            String eventId,
            UUID userId,
            boolean userExists,
            boolean suspended,
            boolean muted,
            Long muteUntil,
            Long banUntil,
            boolean canSendPrivate,
            long occurredAtEpochMillis,
            Long version
    ) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("eventId", eventId);
        values.put("userId", userId);
        values.put("userExists", userExists);
        values.put("suspended", suspended);
        values.put("muted", muted);
        values.put("muteUntil", muteUntil);
        values.put("banUntil", banUntil);
        values.put("canSendPrivate", canSendPrivate);
        values.put("occurredAtEpochMillis", occurredAtEpochMillis);
        values.put("version", version);
        values.put("schemaVersion", ImContractVersions.KAFKA_EVENT_SCHEMA_VERSION);
        return values;
    }

    private static <T> T newRecord(Class<T> recordType, Map<String, Object> values) throws Exception {
        assertTrue(recordType.isRecord(), recordType.getSimpleName() + " must be a record");

        RecordComponent[] components = recordType.getRecordComponents();
        for (String expected : values.keySet()) {
            boolean present = false;
            for (RecordComponent component : components) {
                if (component.getName().equals(expected)) {
                    present = true;
                    break;
                }
            }
            assertTrue(present, recordType.getSimpleName() + " missing component: " + expected);
        }

        Class<?>[] parameterTypes = new Class<?>[components.length];
        Object[] args = new Object[components.length];
        for (int i = 0; i < components.length; i++) {
            parameterTypes[i] = components[i].getType();
            args[i] = constructorValue(components[i], values.get(components[i].getName()));
        }

        Constructor<T> constructor = recordType.getDeclaredConstructor(parameterTypes);
        return constructor.newInstance(args);
    }

    private static Object constructorValue(RecordComponent component, Object value) {
        if (value != null) {
            return value;
        }
        Class<?> type = component.getType();
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == double.class) {
            return 0D;
        }
        if (type == float.class) {
            return 0F;
        }
        if (type == short.class) {
            return (short) 0;
        }
        if (type == byte.class) {
            return (byte) 0;
        }
        if (type == char.class) {
            return '\0';
        }
        return Array.get(Array.newInstance(type, 1), 0);
    }

    private static Object recordComponentValue(Object record, String componentName) throws Exception {
        for (RecordComponent component : record.getClass().getRecordComponents()) {
            if (component.getName().equals(componentName)) {
                return component.getAccessor().invoke(record);
            }
        }
        throw new AssertionError(record.getClass().getSimpleName() + " missing component: " + componentName);
    }

    private static Object classConstant(String className, String fieldName) throws Exception {
        Class<?> type = Class.forName(className);
        return type.getField(fieldName).get(null);
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
