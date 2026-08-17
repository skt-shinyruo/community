package com.nowcoder.community.im.core.application;

import com.nowcoder.community.im.common.command.SendPrivateTextCommand;
import com.nowcoder.community.im.common.policy.PrivateMessagePolicyDecision;
import com.nowcoder.community.im.core.domain.repository.PrivateMessageRepository;
import com.nowcoder.community.im.core.infrastructure.persistence.MyBatisPrivateMessageRepository;
import com.nowcoder.community.im.core.policy.PrivateMessagePolicyVerifier;
import com.nowcoder.community.im.common.support.ConversationIdSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.nowcoder.community.im.core.support.ImCoreTestDatabaseCleaner.cleanPrivateMessages;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
class PrivateMessageTransactionBoundaryTest {

    @Autowired
    private PrivateMessageApplicationService privateMessageApplicationService;

    @Autowired
    private PrivateMessageRepository privateMessageRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private PrivateMessagePolicyVerifier privateMessagePolicyVerifier;

    @MockitoSpyBean
    private MyBatisPrivateMessageRepository privateMessagePersistence;

    @AfterEach
    void cleanPersistedState() {
        cleanPrivateMessages(jdbcTemplate);
    }

    @Test
    void policyVerificationRunsBetweenReplayAndPersistenceTransactions() {
        UUID fromUserId = uuid(101);
        UUID toUserId = uuid(102);
        String conversationId = ConversationIdSupport.conversationId(fromUserId, toUserId);
        SendPrivateTextCommand command = new SendPrivateTextCommand(
                "req-private-transaction-boundary",
                "client-private-transaction-boundary",
                fromUserId,
                toUserId,
                conversationId,
                "hello",
                System.currentTimeMillis()
        );
        AtomicBoolean replayLookupWasTransactional = new AtomicBoolean();
        AtomicBoolean policyVerificationWasTransactional = new AtomicBoolean(true);
        AtomicBoolean persistenceWasTransactional = new AtomicBoolean();
        doAnswer(invocation -> {
            replayLookupWasTransactional.set(TransactionSynchronizationManager.isActualTransactionActive());
            return invocation.callRealMethod();
        }).when(privateMessagePersistence).findByIdempotency(
                conversationId,
                fromUserId,
                command.clientMsgId()
        );
        when(privateMessagePolicyVerifier.verify(fromUserId, toUserId)).thenAnswer(invocation -> {
            policyVerificationWasTransactional.set(TransactionSynchronizationManager.isActualTransactionActive());
            return PrivateMessagePolicyDecision.allow();
        });
        doAnswer(invocation -> {
            persistenceWasTransactional.set(TransactionSynchronizationManager.isActualTransactionActive());
            return invocation.callRealMethod();
        }).when(privateMessagePersistence).insert(any());

        privateMessageApplicationService.persist(command);

        assertThat(replayLookupWasTransactional)
                .as("idempotent replay lookup must use its own short transaction")
                .isTrue();
        assertThat(policyVerificationWasTransactional)
                .as("owner policy verification must not hold an IM database transaction")
                .isFalse();
        assertThat(persistenceWasTransactional)
                .as("message, inbox, and outbox writes must use the final transaction")
                .isTrue();
        assertThat(privateMessageRepository.listAfterSeq(conversationId, 0, 10)).hasSize(1);
    }

    private static UUID uuid(long suffix) {
        return UUID.fromString("00000000-0000-7000-8000-" + String.format("%012x", suffix));
    }
}
