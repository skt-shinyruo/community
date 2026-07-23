package com.nowcoder.community.im.core.application;

import com.nowcoder.community.common.json.JacksonJsonCodec;
import com.nowcoder.community.common.json.JsonMappers;
import com.nowcoder.community.im.core.domain.model.ConversationListItem;
import com.nowcoder.community.im.core.domain.repository.ConversationReadStateRepository;
import com.nowcoder.community.im.core.domain.repository.ConversationRepository;
import com.nowcoder.community.im.core.domain.repository.PrivateMessageRepository;
import com.nowcoder.community.im.core.domain.repository.UserInboxRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConversationApplicationServiceCursorPaginationTest {

    @Test
    void shouldPageConversationsWithAnExclusiveStableBoundary() {
        UUID viewerId = uuid(1);
        Instant newestSortAt = Instant.parse("2026-07-21T03:00:00Z");
        Instant tiedSortAt = Instant.parse("2026-07-21T02:00:00Z");
        Instant oldestSortAt = Instant.parse("2026-07-21T01:00:00Z");
        ConversationListItem newest = item("conversation-newest", newestSortAt);
        ConversationListItem tiedFirst = item("conversation-a", tiedSortAt);
        ConversationListItem tiedSecond = item("conversation-b", tiedSortAt);
        ConversationListItem oldest = item("conversation-oldest", oldestSortAt);

        UserInboxRepository userInboxRepository = mock(UserInboxRepository.class);
        when(userInboxRepository.listConversationsBefore(viewerId, null, null, 3))
                .thenReturn(List.of(newest, tiedFirst, tiedSecond));
        when(userInboxRepository.listConversationsBefore(viewerId, tiedSortAt, "conversation-a", 3))
                .thenReturn(List.of(tiedSecond, oldest));

        ConversationApplicationService applicationService = applicationService(userInboxRepository);

        var firstPage = applicationService.listConversationPage(viewerId, null, 2);
        var secondPage = applicationService.listConversationPage(viewerId, firstPage.nextCursor(), 2);

        assertThat(firstPage.items()).extracting(item -> item.conversationId())
                .containsExactly("conversation-newest", "conversation-a");
        assertThat(firstPage.hasMore()).isTrue();
        assertThat(firstPage.nextCursor()).isNotBlank();
        assertThat(secondPage.items()).extracting(item -> item.conversationId())
                .containsExactly("conversation-b", "conversation-oldest");
        assertThat(secondPage.hasMore()).isFalse();
        assertThat(secondPage.nextCursor()).isNull();
    }

    private ConversationApplicationService applicationService(UserInboxRepository userInboxRepository) {
        return new ConversationApplicationService(
                mock(PrivateMessageRepository.class),
                mock(ConversationReadStateRepository.class),
                mock(ConversationRepository.class),
                userInboxRepository,
                new ConversationCursorCodec(new JacksonJsonCodec(JsonMappers.standard()))
        );
    }

    private ConversationListItem item(String conversationId, Instant sortAt) {
        return new ConversationListItem(conversationId, uuid(2), 1L, 0L, 1L, null, sortAt);
    }

    private static UUID uuid(long suffix) {
        return UUID.fromString("00000000-0000-7000-8000-" + String.format("%012x", suffix));
    }
}
