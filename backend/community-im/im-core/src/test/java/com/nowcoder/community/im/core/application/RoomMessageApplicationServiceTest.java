package com.nowcoder.community.im.core.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.im.common.command.SendRoomTextCommand;
import com.nowcoder.community.im.core.domain.model.RoomMessageRecord;
import com.nowcoder.community.im.core.domain.repository.RoomMessageRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class RoomMessageApplicationServiceTest {

    @Autowired
    private RoomApplicationService roomApplicationService;

    @Autowired
    private RoomMessageApplicationService roomMessageApplicationService;

    @Autowired
    private RoomMessageRepository roomMessageRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void persist_isIdempotentByClientMsgId() {
        UUID sender = uuid(1);
        UUID roomId = roomApplicationService.createRoom(sender, "room").roomId();

        SendRoomTextCommand cmd = new SendRoomTextCommand(
                "req-1",
                "c1",
                sender,
                roomId,
                "hi",
                System.currentTimeMillis()
        );

        var e1 = roomMessageApplicationService.persist(cmd);
        var e2 = roomMessageApplicationService.persist(cmd);

        assertThat(e2.messageId()).isEqualTo(e1.messageId());
        assertThat(e2.seq()).isEqualTo(e1.seq());

        List<RoomMessageRecord> rows = roomMessageRepository.listAfterSeq(roomId, 0, 100);
        assertThat(rows).hasSize(1);
    }

    @Test
    void persist_enqueuesRoomPersistedOutboxEvent() {
        UUID sender = uuid(1);
        UUID roomId = roomApplicationService.createRoom(sender, "room").roomId();

        SendRoomTextCommand cmd = new SendRoomTextCommand(
                "req-2",
                "c2",
                sender,
                roomId,
                "hi",
                System.currentTimeMillis()
        );

        roomMessageApplicationService.persist(cmd);

        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from outbox_event where event_id = ? and event_key = ?",
                Integer.class,
                "im:rf:" + roomId + ":" + eventSeq(roomId),
                roomId.toString()
        );
        assertThat(count).isEqualTo(1);
    }

    @Test
    void persist_firstSendEnqueuesFactAndCommittedAttemptEventsWithSeparateIdentities() throws Exception {
        UUID sender = uuid(11);
        UUID roomId = roomApplicationService.createRoom(sender, "room").roomId();
        SendRoomTextCommand cmd = command("req-first-room", "c-first-room", sender, roomId);

        var event = roomMessageApplicationService.persist(cmd);

        String factEventId = "im:rf:" + roomId + ":" + event.seq();
        assertThat(event.eventId()).isEqualTo(factEventId);
        assertOutboxRow(factEventId, "im.event.room-persisted", roomId.toString());

        JsonNode factPayload = objectMapper.readTree(outboxPayload(factEventId));
        assertThat(factPayload.has("requestId")).isFalse();
        assertThat(factPayload.has("clientMsgId")).isFalse();
        assertThat(factPayload.path("roomId").asText()).isEqualTo(roomId.toString());
        assertThat(factPayload.path("seq").asLong()).isEqualTo(event.seq());

        String committedEventId = roomSendResultEventId(cmd.requestId(), cmd.clientMsgId(), sender);
        assertOutboxRow(committedEventId, "im.event.room-committed", roomId.toString());

        JsonNode committedPayload = objectMapper.readTree(outboxPayload(committedEventId));
        assertThat(committedPayload.path("requestId").asText()).isEqualTo("req-first-room");
        assertThat(committedPayload.path("clientMsgId").asText()).isEqualTo("c-first-room");
        assertThat(committedPayload.path("fromUserId").asText()).isEqualTo(sender.toString());
        assertThat(committedPayload.path("messageId").asText()).isEqualTo(event.messageId().toString());
        assertThat(committedPayload.path("seq").asLong()).isEqualTo(event.seq());
    }

    @Test
    void persist_duplicateCommandDoesNotDuplicateFactOrAttemptResultOutboxEvents() {
        UUID sender = uuid(12);
        UUID roomId = roomApplicationService.createRoom(sender, "room").roomId();
        SendRoomTextCommand cmd = command("req-dup-room", "c-dup-room", sender, roomId);

        var first = roomMessageApplicationService.persist(cmd);
        var second = roomMessageApplicationService.persist(cmd);

        assertThat(second.messageId()).isEqualTo(first.messageId());
        assertThat(outboxCount("im:rf:" + roomId + ":" + first.seq())).isEqualTo(1);
        assertThat(outboxCount(roomSendResultEventId(cmd.requestId(), cmd.clientMsgId(), sender))).isEqualTo(1);
        List<RoomMessageRecord> rows = roomMessageRepository.listAfterSeq(roomId, 0, 100);
        assertThat(rows).hasSize(1);
    }

    @Test
    void persist_sameMessageDifferentRequestIdReusesFactButEnqueuesCurrentAttemptCommittedResult() {
        UUID sender = uuid(13);
        UUID roomId = roomApplicationService.createRoom(sender, "room").roomId();
        SendRoomTextCommand firstCommand = command("req-room-a", "c-same-room", sender, roomId);
        SendRoomTextCommand replayCommand = command("req-room-b", "c-same-room", sender, roomId);

        var first = roomMessageApplicationService.persist(firstCommand);
        var replay = roomMessageApplicationService.persist(replayCommand);

        assertThat(replay.messageId()).isEqualTo(first.messageId());
        assertThat(replay.seq()).isEqualTo(first.seq());
        assertThat(outboxCount("im:rf:" + roomId + ":" + first.seq())).isEqualTo(1);
        assertThat(outboxCount(roomSendResultEventId(firstCommand.requestId(), firstCommand.clientMsgId(), sender))).isEqualTo(1);
        assertThat(outboxCount(roomSendResultEventId(replayCommand.requestId(), replayCommand.clientMsgId(), sender))).isEqualTo(1);
        List<RoomMessageRecord> rows = roomMessageRepository.listAfterSeq(roomId, 0, 100);
        assertThat(rows).hasSize(1);
    }

    @Test
    void persist_maintainsUserRoomInboxWithoutDoubleUnreadOnReplay() {
        UUID sender = uuid(21);
        UUID receiver = uuid(22);
        UUID roomId = roomApplicationService.createRoom(sender, "room").roomId();
        roomApplicationService.joinRoom(receiver, roomId);
        SendRoomTextCommand cmd = command("req-room-inbox", "c-room-inbox", sender, roomId);

        var first = roomMessageApplicationService.persist(cmd);
        var second = roomMessageApplicationService.persist(cmd);

        assertThat(second.messageId()).isEqualTo(first.messageId());
        assertRoomInbox(sender, roomId, first.seq(), first.messageId(), 1L, 0L);
        assertRoomInbox(receiver, roomId, first.seq(), first.messageId(), 0L, 1L);
    }

    private SendRoomTextCommand command(String requestId, String clientMsgId, UUID sender, UUID roomId) {
        return new SendRoomTextCommand(
                requestId,
                clientMsgId,
                sender,
                roomId,
                "hi",
                System.currentTimeMillis()
        );
    }

    private void assertRoomInbox(
            UUID userId,
            UUID roomId,
            long lastSeq,
            UUID lastMessageId,
            long lastReadSeq,
            long unreadCount
    ) {
        List<RoomInboxRow> rows = jdbcTemplate.query(
                "select last_seq, last_message_id, last_read_seq, unread_count " +
                        "from im_user_room_inbox where user_id = ? and room_id = ?",
                (rs, rowNum) -> new RoomInboxRow(
                        rs.getLong("last_seq"),
                        BinaryUuidTestCodec.fromBytes(rs.getBytes("last_message_id")),
                        rs.getLong("last_read_seq"),
                        rs.getLong("unread_count")
                ),
                BinaryUuidTestCodec.toBytes(userId),
                BinaryUuidTestCodec.toBytes(roomId)
        );
        assertThat(rows).hasSize(1);
        RoomInboxRow row = rows.get(0);
        assertThat(row.lastSeq()).isEqualTo(lastSeq);
        assertThat(row.lastMessageId()).isEqualTo(lastMessageId);
        assertThat(row.lastReadSeq()).isEqualTo(lastReadSeq);
        assertThat(row.unreadCount()).isEqualTo(unreadCount);
    }

    private record RoomInboxRow(long lastSeq, UUID lastMessageId, long lastReadSeq, long unreadCount) {
    }

    private void assertOutboxRow(String eventId, String topic, String eventKey) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from outbox_event where event_id = ? and topic = ? and event_key = ?",
                Integer.class,
                eventId,
                topic,
                eventKey
        );
        assertThat(count).isEqualTo(1);
    }

    private int outboxCount(String eventId) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from outbox_event where event_id = ?",
                Integer.class,
                eventId
        );
        return count == null ? 0 : count;
    }

    private String outboxPayload(String eventId) {
        return jdbcTemplate.queryForObject(
                "select payload from outbox_event where event_id = ?",
                String.class,
                eventId
        );
    }

    private long eventSeq(UUID roomId) {
        List<RoomMessageRecord> rows = roomMessageRepository.listAfterSeq(roomId, 0, 100);
        assertThat(rows).hasSize(1);
        return rows.get(0).seq();
    }

    private static String roomSendResultEventId(String requestId, String clientMsgId, UUID fromUserId) {
        return "im:rsr:" + digestAttempt(requestId, clientMsgId, fromUserId);
    }

    private static String digestAttempt(String requestId, String clientMsgId, UUID fromUserId) {
        try {
            String source = normalize(fromUserId) + "|" + normalize(requestId) + "|" + normalize(clientMsgId);
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(source.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String normalize(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    private static UUID uuid(long suffix) {
        return UUID.fromString("00000000-0000-7000-8000-" + String.format("%012x", suffix));
    }

    private static final class BinaryUuidTestCodec {

        private BinaryUuidTestCodec() {
        }

        static byte[] toBytes(UUID uuid) {
            byte[] bytes = new byte[16];
            long most = uuid.getMostSignificantBits();
            long least = uuid.getLeastSignificantBits();
            for (int i = 0; i < 8; i++) {
                bytes[i] = (byte) (most >>> (8 * (7 - i)));
                bytes[i + 8] = (byte) (least >>> (8 * (7 - i)));
            }
            return bytes;
        }

        static UUID fromBytes(byte[] bytes) {
            if (bytes == null) {
                return null;
            }
            long most = 0L;
            long least = 0L;
            for (int i = 0; i < 8; i++) {
                most = (most << 8) | (bytes[i] & 0xffL);
                least = (least << 8) | (bytes[i + 8] & 0xffL);
            }
            return new UUID(most, least);
        }
    }
}
