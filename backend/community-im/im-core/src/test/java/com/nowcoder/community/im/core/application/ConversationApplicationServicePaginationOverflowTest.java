package com.nowcoder.community.im.core.application;

import com.nowcoder.community.im.core.domain.repository.ConversationReadStateRepository;
import com.nowcoder.community.im.core.domain.repository.ConversationRepository;
import com.nowcoder.community.im.core.domain.repository.PrivateMessageRepository;
import com.nowcoder.community.im.core.domain.repository.UserInboxRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConversationApplicationServicePaginationOverflowTest {

    @Test
    void listConversationsShouldNotPassNegativeOffsetWhenPageIsHuge() {
        UserInboxRepository userInboxRepository = mock(UserInboxRepository.class);

        AtomicLong capturedOffset = new AtomicLong(-1L);
        when(userInboxRepository.listConversations(any(UUID.class), anyInt(), anyLong()))
                .thenAnswer(invocation -> {
                    capturedOffset.set(invocation.getArgument(2, Long.class));
                    return List.of();
                });

        ConversationApplicationService applicationService = new ConversationApplicationService(
                mock(PrivateMessageRepository.class),
                mock(ConversationReadStateRepository.class),
                mock(ConversationRepository.class),
                userInboxRepository,
                mock(ConversationCursorCodec.class)
        );

        applicationService.listConversations(
                UUID.fromString("00000000-0000-7000-8000-000000000001"),
                Integer.MAX_VALUE,
                200
        );

        assertThat(capturedOffset.get()).isGreaterThanOrEqualTo(0L);
    }
}
