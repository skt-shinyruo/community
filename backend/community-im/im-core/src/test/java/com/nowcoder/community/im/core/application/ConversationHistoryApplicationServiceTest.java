package com.nowcoder.community.im.core.application;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.exception.CommonErrorCode;
import com.nowcoder.community.common.json.JacksonJsonCodec;
import com.nowcoder.community.common.json.JsonMappers;
import com.nowcoder.community.im.common.command.SendPrivateTextCommand;
import com.nowcoder.community.im.common.policy.PrivateMessagePolicyDecision;
import com.nowcoder.community.im.core.domain.model.PrivateMessageRecord;
import com.nowcoder.community.im.core.domain.repository.ConversationReadStateRepository;
import com.nowcoder.community.im.core.domain.repository.ConversationRepository;
import com.nowcoder.community.im.core.domain.repository.PrivateMessageRepository;
import com.nowcoder.community.im.core.domain.repository.UserInboxRepository;
import com.nowcoder.community.im.core.policy.PrivateMessagePolicyVerifier;
import com.nowcoder.community.im.core.support.ConversationIdSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ConversationHistoryApplicationServiceTest {

    @Autowired
    private ConversationApplicationService conversationApplicationService;

    @Autowired
    private PrivateMessageApplicationService privateMessageApplicationService;

    @MockBean
    private PrivateMessagePolicyVerifier privateMessagePolicyVerifier;

    @BeforeEach
    void allowPrivateMessages() {
        when(privateMessagePolicyVerifier.verify(any(UUID.class), any(UUID.class)))
                .thenReturn(PrivateMessagePolicyDecision.allow());
    }

    @Test
    void shouldLoadLatestMessagesThenEarlierHistoryWithoutDuplicatesOrGaps() {
        UUID sender = uuid(1);
        UUID receiver = uuid(2);
        String conversationId = ConversationIdSupport.conversationId(sender, receiver);
        for (long sequence = 1; sequence <= 75; sequence++) {
            privateMessageApplicationService.persist(new SendPrivateTextCommand(
                    "history-request-" + sequence,
                    "history-client-" + sequence,
                    sender,
                    receiver,
                    conversationId,
                    "message-" + sequence,
                    System.currentTimeMillis()
            ));
        }

        var latest = conversationApplicationService.listMessageHistory(receiver, conversationId, null, 50);
        var earlier = conversationApplicationService.listMessageHistory(
                receiver,
                conversationId,
                latest.nextBeforeSeq(),
                50
        );

        assertThat(latest.items()).extracting(item -> item.seq())
                .containsExactlyElementsOf(LongStream.rangeClosed(26, 75).boxed().toList());
        assertThat(latest.hasMore()).isTrue();
        assertThat(latest.nextBeforeSeq()).isEqualTo(26L);
        assertThat(latest.lastReadSeq()).isZero();
        assertThat(earlier.items()).extracting(item -> item.seq())
                .containsExactlyElementsOf(LongStream.rangeClosed(1, 25).boxed().toList());
        assertThat(earlier.hasMore()).isFalse();
        assertThat(earlier.nextBeforeSeq()).isNull();
        assertThat(earlier.lastReadSeq()).isZero();
    }

    @Test
    void shouldReturnAnEmptyHistoryForAConversationThatDoesNotExist() {
        UUID userA = uuid(10);
        UUID userB = uuid(11);
        String conversationId = ConversationIdSupport.conversationId(userA, userB);

        var history = conversationApplicationService.listMessageHistory(userA, conversationId, null, 50);

        assertThat(history.conversationId()).isEqualTo(conversationId);
        assertThat(history.items()).isEmpty();
        assertThat(history.hasMore()).isFalse();
        assertThat(history.nextBeforeSeq()).isNull();
        assertThat(history.lastReadSeq()).isZero();
    }

    @Test
    void shouldRejectHistoryAccessForUsersOutsideTheConversation() {
        UUID userA = uuid(20);
        UUID userB = uuid(21);
        String conversationId = ConversationIdSupport.conversationId(userA, userB);

        assertThatThrownBy(() -> conversationApplicationService.listMessageHistory(uuid(22), conversationId, null, 50))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void shouldRejectNonPositiveHistoryBoundaries() {
        UUID userA = uuid(30);
        UUID userB = uuid(31);
        String conversationId = ConversationIdSupport.conversationId(userA, userB);

        for (Long beforeSeq : List.of(0L, -1L)) {
            assertThatThrownBy(() -> conversationApplicationService.listMessageHistory(userA, conversationId, beforeSeq, 50))
                    .isInstanceOfSatisfying(BusinessException.class, error ->
                            assertThat(error.getErrorCode()).isEqualTo(CommonErrorCode.INVALID_ARGUMENT));
        }
    }

    @Test
    void shouldReadTheHistoryProbeOnlyOnceBeforeRestoringAscendingOrder() {
        UUID sender = uuid(40);
        UUID receiver = uuid(41);
        String conversationId = ConversationIdSupport.conversationId(sender, receiver);
        List<PrivateMessageRecord> newestFirst = LongStream.iterate(75L, value -> value - 1L)
                .limit(51)
                .mapToObj(sequence -> new PrivateMessageRecord(
                        conversationId,
                        sequence,
                        uuid(100 + sequence),
                        sender,
                        receiver,
                        "message-" + sequence,
                        "client-" + sequence,
                        Instant.parse("2026-07-21T01:02:03Z")
                ))
                .toList();
        PrivateMessageRepository privateMessageRepository = mock(PrivateMessageRepository.class);
        when(privateMessageRepository.listBeforeSeq(conversationId, null, 51)).thenReturn(newestFirst);
        ConversationRepository conversationRepository = mock(ConversationRepository.class);
        when(conversationRepository.exists(conversationId)).thenReturn(true);

        ConversationApplicationService applicationService = new ConversationApplicationService(
                privateMessageRepository,
                mock(ConversationReadStateRepository.class),
                conversationRepository,
                mock(UserInboxRepository.class),
                new ConversationCursorCodec(new JacksonJsonCodec(JsonMappers.standard()))
        );

        var history = applicationService.listMessageHistory(sender, conversationId, null, 50);

        assertThat(history.items()).extracting(item -> item.seq())
                .containsExactlyElementsOf(LongStream.rangeClosed(26, 75).boxed().toList());
        assertThat(history.hasMore()).isTrue();
        assertThat(history.nextBeforeSeq()).isEqualTo(26L);
        verify(privateMessageRepository, times(1)).listBeforeSeq(conversationId, null, 51);
    }

    private static UUID uuid(long suffix) {
        return UUID.fromString("00000000-0000-7000-8000-" + String.format("%012x", suffix));
    }
}
