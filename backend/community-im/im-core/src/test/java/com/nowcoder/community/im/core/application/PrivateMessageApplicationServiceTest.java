package com.nowcoder.community.im.core.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.im.common.command.SendPrivateTextCommand;
import com.nowcoder.community.im.common.policy.PrivateMessagePolicyDecision;
import com.nowcoder.community.im.core.domain.model.PrivateMessageRecord;
import com.nowcoder.community.im.core.domain.repository.ConversationReadStateRepository;
import com.nowcoder.community.im.core.domain.repository.PrivateMessageRepository;
import com.nowcoder.community.im.core.policy.PrivateMessagePolicyVerifier;
import com.nowcoder.community.im.core.support.ConversationIdSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PrivateMessageApplicationServiceTest {

    @Autowired
    private PrivateMessageApplicationService privateMessageApplicationService;

    @Autowired
    private PrivateMessageRepository privateMessageRepository;

    @Autowired
    private ConversationReadStateRepository readStateRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PrivateMessagePolicyVerifier privateMessagePolicyVerifier;

    @BeforeEach
    void setUp() {
        when(privateMessagePolicyVerifier.verify(any(UUID.class), any(UUID.class)))
                .thenReturn(PrivateMessagePolicyDecision.allow());
    }

    @Test
    void persist_isIdempotentByClientMsgId() {
        UUID fromUserId = uuid(1);
        UUID toUserId = uuid(2);
        String conversationId = ConversationIdSupport.conversationId(fromUserId, toUserId);

        SendPrivateTextCommand cmd = new SendPrivateTextCommand(
                "req-1",
                "c1",
                fromUserId,
                toUserId,
                conversationId,
                "hello",
                System.currentTimeMillis()
        );

        var e1 = privateMessageApplicationService.persist(cmd);
        var e2 = privateMessageApplicationService.persist(cmd);

        assertThat(e2.messageId()).isEqualTo(e1.messageId());
        assertThat(e2.seq()).isEqualTo(e1.seq());

        List<PrivateMessageRecord> rows = privateMessageRepository.listAfterSeq(conversationId, 0, 100);
        assertThat(rows).hasSize(1);
    }

    @Test
    void persist_shouldReturnExistingMessageBeforePolicyCheckForIdempotentReplay() {
        UUID fromUserId = uuid(1);
        UUID toUserId = uuid(2);
        String conversationId = ConversationIdSupport.conversationId(fromUserId, toUserId);
        SendPrivateTextCommand cmd = command("req-idempotent-policy", "c-idempotent-policy", fromUserId, toUserId, conversationId);
        when(privateMessagePolicyVerifier.verify(fromUserId, toUserId))
                .thenReturn(PrivateMessagePolicyDecision.allow())
                .thenReturn(PrivateMessagePolicyDecision.deny(403, "policy_denied", "用户已拉黑"));

        var first = privateMessageApplicationService.persist(cmd);
        var second = privateMessageApplicationService.persist(cmd);

        assertThat(second.messageId()).isEqualTo(first.messageId());
        assertThat(second.seq()).isEqualTo(first.seq());
        verify(privateMessagePolicyVerifier, times(1)).verify(fromUserId, toUserId);
        List<PrivateMessageRecord> rows = privateMessageRepository.listAfterSeq(conversationId, 0, 100);
        assertThat(rows).hasSize(1);
    }

    @Test
    void persist_marksSenderReadWatermarkToSeq() {
        UUID fromUserId = uuid(1);
        UUID toUserId = uuid(2);
        String conversationId = ConversationIdSupport.conversationId(fromUserId, toUserId);

        SendPrivateTextCommand cmd = new SendPrivateTextCommand(
                "req-2",
                "c2",
                fromUserId,
                toUserId,
                conversationId,
                "hello",
                System.currentTimeMillis()
        );

        var e = privateMessageApplicationService.persist(cmd);
        assertThat(readStateRepository.getLastReadSeq(conversationId, fromUserId)).isEqualTo(e.seq());
    }

    @Test
    void persist_enqueuesPrivatePersistedOutboxEvent() {
        UUID fromUserId = uuid(1);
        UUID toUserId = uuid(2);
        String conversationId = ConversationIdSupport.conversationId(fromUserId, toUserId);

        SendPrivateTextCommand cmd = new SendPrivateTextCommand(
                "req-3",
                "c3",
                fromUserId,
                toUserId,
                conversationId,
                "hello",
                System.currentTimeMillis()
        );

        privateMessageApplicationService.persist(cmd);

        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from outbox_event where event_id = ? and event_key = ?",
                Integer.class,
                "im:pf:" + eventMessageId(conversationId),
                conversationId
        );
        assertThat(count).isEqualTo(1);
    }

    @Test
    void persist_firstSendEnqueuesFactAndCommittedAttemptEventsWithSeparateIdentities() throws Exception {
        UUID fromUserId = uuid(11);
        UUID toUserId = uuid(12);
        String conversationId = ConversationIdSupport.conversationId(fromUserId, toUserId);
        SendPrivateTextCommand cmd = command("req-first-private", "c-first-private", fromUserId, toUserId, conversationId);

        var event = privateMessageApplicationService.persist(cmd);

        String factEventId = "im:pf:" + event.messageId();
        assertThat(event.eventId()).isEqualTo(factEventId);
        assertOutboxRow(factEventId, "im.event.private-persisted", conversationId);

        JsonNode factPayload = objectMapper.readTree(outboxPayload(factEventId));
        assertThat(factPayload.has("requestId")).isFalse();
        assertThat(factPayload.has("clientMsgId")).isFalse();
        assertThat(factPayload.path("messageId").asText()).isEqualTo(event.messageId().toString());

        String committedEventId = privateSendResultEventId(cmd.requestId(), cmd.clientMsgId(), fromUserId);
        assertOutboxRow(committedEventId, "im.event.private-committed", conversationId);

        JsonNode committedPayload = objectMapper.readTree(outboxPayload(committedEventId));
        assertThat(committedPayload.path("requestId").asText()).isEqualTo("req-first-private");
        assertThat(committedPayload.path("clientMsgId").asText()).isEqualTo("c-first-private");
        assertThat(committedPayload.path("fromUserId").asText()).isEqualTo(fromUserId.toString());
        assertThat(committedPayload.path("messageId").asText()).isEqualTo(event.messageId().toString());
        assertThat(committedPayload.path("seq").asLong()).isEqualTo(event.seq());
    }

    @Test
    void persist_duplicateCommandDoesNotDuplicateFactOrAttemptResultOutboxEvents() {
        UUID fromUserId = uuid(13);
        UUID toUserId = uuid(14);
        String conversationId = ConversationIdSupport.conversationId(fromUserId, toUserId);
        SendPrivateTextCommand cmd = command("req-dup-private", "c-dup-private", fromUserId, toUserId, conversationId);

        var first = privateMessageApplicationService.persist(cmd);
        var second = privateMessageApplicationService.persist(cmd);

        assertThat(second.messageId()).isEqualTo(first.messageId());
        assertThat(outboxCount("im:pf:" + first.messageId())).isEqualTo(1);
        assertThat(outboxCount(privateSendResultEventId(cmd.requestId(), cmd.clientMsgId(), fromUserId))).isEqualTo(1);
        List<PrivateMessageRecord> rows = privateMessageRepository.listAfterSeq(conversationId, 0, 100);
        assertThat(rows).hasSize(1);
    }

    @Test
    void persist_maintainsUserConversationInboxWithoutDoubleUnreadOnReplay() {
        UUID fromUserId = uuid(21);
        UUID toUserId = uuid(22);
        String conversationId = ConversationIdSupport.conversationId(fromUserId, toUserId);
        SendPrivateTextCommand cmd = command("req-inbox-private", "c-inbox-private", fromUserId, toUserId, conversationId);

        var first = privateMessageApplicationService.persist(cmd);
        var second = privateMessageApplicationService.persist(cmd);

        assertThat(second.messageId()).isEqualTo(first.messageId());
        assertConversationInbox(fromUserId, conversationId, toUserId, first.seq(), first.messageId(), 1L, 0L);
        assertConversationInbox(toUserId, conversationId, fromUserId, first.seq(), first.messageId(), 0L, 1L);
    }

    @Test
    void persist_sameMessageDifferentRequestIdReusesFactButEnqueuesCurrentAttemptCommittedResult() {
        UUID fromUserId = uuid(15);
        UUID toUserId = uuid(16);
        String conversationId = ConversationIdSupport.conversationId(fromUserId, toUserId);
        SendPrivateTextCommand firstCommand = command("req-private-a", "c-same-private", fromUserId, toUserId, conversationId);
        SendPrivateTextCommand replayCommand = command("req-private-b", "c-same-private", fromUserId, toUserId, conversationId);

        var first = privateMessageApplicationService.persist(firstCommand);
        var replay = privateMessageApplicationService.persist(replayCommand);

        assertThat(replay.messageId()).isEqualTo(first.messageId());
        assertThat(replay.seq()).isEqualTo(first.seq());
        assertThat(outboxCount("im:pf:" + first.messageId())).isEqualTo(1);
        assertThat(outboxCount(privateSendResultEventId(firstCommand.requestId(), firstCommand.clientMsgId(), fromUserId))).isEqualTo(1);
        assertThat(outboxCount(privateSendResultEventId(replayCommand.requestId(), replayCommand.clientMsgId(), fromUserId))).isEqualTo(1);

        List<PrivateMessageRecord> rows = privateMessageRepository.listAfterSeq(conversationId, 0, 100);
        assertThat(rows).hasSize(1);
    }

    @Test
    void persist_shouldRejectMutedSenderBeforePersistenceWhenRealtimeProjectionIsStale() {
        UUID fromUserId = uuid(1);
        UUID toUserId = uuid(2);
        String conversationId = ConversationIdSupport.conversationId(fromUserId, toUserId);
        SendPrivateTextCommand cmd = command("req-muted", "c-muted", fromUserId, toUserId, conversationId);
        when(privateMessagePolicyVerifier.verify(fromUserId, toUserId))
                .thenReturn(new PrivateMessagePolicyDecision(
                        false,
                        403,
                        "policy_denied",
                        "发送方无权限发送私信",
                        System.currentTimeMillis()
                ));

        assertThatThrownBy(() -> privateMessageApplicationService.persist(cmd))
                .isInstanceOf(PrivateMessagePolicyVerifier.PrivateMessagePolicyRejectedException.class)
                .hasMessage("发送方无权限发送私信");

        assertNoPrivateMessages(conversationId);
    }

    @Test
    void persist_shouldRejectBlockedUsersBeforePersistenceWhenRealtimeProjectionIsStale() {
        UUID fromUserId = uuid(1);
        UUID toUserId = uuid(2);
        String conversationId = ConversationIdSupport.conversationId(fromUserId, toUserId);
        SendPrivateTextCommand cmd = command("req-blocked", "c-blocked", fromUserId, toUserId, conversationId);
        when(privateMessagePolicyVerifier.verify(fromUserId, toUserId))
                .thenReturn(new PrivateMessagePolicyDecision(
                        false,
                        403,
                        "policy_denied",
                        "用户已拉黑",
                        System.currentTimeMillis()
                ));

        assertThatThrownBy(() -> privateMessageApplicationService.persist(cmd))
                .isInstanceOf(PrivateMessagePolicyVerifier.PrivateMessagePolicyRejectedException.class)
                .hasMessage("用户已拉黑");

        assertNoPrivateMessages(conversationId);
    }

    @Test
    void persist_shouldRejectMissingTargetUserBeforePersistenceWhenRealtimeProjectionIsStale() {
        UUID fromUserId = uuid(1);
        UUID toUserId = uuid(2);
        String conversationId = ConversationIdSupport.conversationId(fromUserId, toUserId);
        SendPrivateTextCommand cmd = command("req-missing-target", "c-missing-target", fromUserId, toUserId, conversationId);
        when(privateMessagePolicyVerifier.verify(fromUserId, toUserId))
                .thenReturn(new PrivateMessagePolicyDecision(
                        false,
                        404,
                        "policy_denied",
                        "接收方不存在",
                        System.currentTimeMillis()
                ));

        assertThatThrownBy(() -> privateMessageApplicationService.persist(cmd))
                .isInstanceOf(PrivateMessagePolicyVerifier.PrivateMessagePolicyRejectedException.class)
                .hasMessage("接收方不存在");

        assertNoPrivateMessages(conversationId);
    }

    private SendPrivateTextCommand command(
            String requestId,
            String clientMsgId,
            UUID fromUserId,
            UUID toUserId,
            String conversationId
    ) {
        return new SendPrivateTextCommand(
                requestId,
                clientMsgId,
                fromUserId,
                toUserId,
                conversationId,
                "hello",
                System.currentTimeMillis()
        );
    }

    private void assertNoPrivateMessages(String conversationId) {
        List<PrivateMessageRecord> rows = privateMessageRepository.listAfterSeq(conversationId, 0, 100);
        assertThat(rows).isEmpty();
    }

    private UUID eventMessageId(String conversationId) {
        List<PrivateMessageRecord> rows = privateMessageRepository.listAfterSeq(conversationId, 0, 100);
        assertThat(rows).hasSize(1);
        return rows.get(0).messageId();
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

    private void assertConversationInbox(
            UUID userId,
            String conversationId,
            UUID peerUserId,
            long lastSeq,
            UUID lastMessageId,
            long lastReadSeq,
            long unreadCount
    ) {
        List<JsonNode> rows = jdbcTemplate.query(
                "select peer_user_id, last_seq, last_message_id, last_read_seq, unread_count " +
                        "from im_user_conversation_inbox where user_id = ? and conversation_id = ?",
                (rs, rowNum) -> objectMapper.createObjectNode()
                        .put("peerUserId", BinaryUuidTestCodec.fromBytes(rs.getBytes("peer_user_id")).toString())
                        .put("lastSeq", rs.getLong("last_seq"))
                        .put("lastMessageId", BinaryUuidTestCodec.fromBytes(rs.getBytes("last_message_id")).toString())
                        .put("lastReadSeq", rs.getLong("last_read_seq"))
                        .put("unreadCount", rs.getLong("unread_count")),
                BinaryUuidTestCodec.toBytes(userId),
                conversationId
        );
        assertThat(rows).hasSize(1);
        JsonNode row = rows.get(0);
        assertThat(row.path("peerUserId").asText()).isEqualTo(peerUserId.toString());
        assertThat(row.path("lastSeq").asLong()).isEqualTo(lastSeq);
        assertThat(row.path("lastMessageId").asText()).isEqualTo(lastMessageId.toString());
        assertThat(row.path("lastReadSeq").asLong()).isEqualTo(lastReadSeq);
        assertThat(row.path("unreadCount").asLong()).isEqualTo(unreadCount);
    }

    private String outboxPayload(String eventId) {
        return jdbcTemplate.queryForObject(
                "select payload from outbox_event where event_id = ?",
                String.class,
                eventId
        );
    }

    private static String privateSendResultEventId(String requestId, String clientMsgId, UUID fromUserId) {
        return "im:psr:" + digestAttempt(requestId, clientMsgId, fromUserId);
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
