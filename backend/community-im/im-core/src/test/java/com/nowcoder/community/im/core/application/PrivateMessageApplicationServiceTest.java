package com.nowcoder.community.im.core.application;

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
        assertThat(e1.requestId()).isEqualTo("req-1");
        assertThat(e1.clientMsgId()).isEqualTo("c1");

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
        assertThat(e.requestId()).isEqualTo("req-2");
        assertThat(e.clientMsgId()).isEqualTo("c2");
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
                "req-3:private_persisted",
                conversationId
        );
        assertThat(count).isEqualTo(1);
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

    private static UUID uuid(long suffix) {
        return UUID.fromString("00000000-0000-7000-8000-" + String.format("%012x", suffix));
    }
}
