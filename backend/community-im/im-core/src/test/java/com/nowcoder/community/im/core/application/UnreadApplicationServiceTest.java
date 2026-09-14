package com.nowcoder.community.im.core.application;

import com.nowcoder.community.im.core.application.result.UnreadSummaryResult;
import com.nowcoder.community.im.core.domain.model.ConversationUnreadItem;
import com.nowcoder.community.im.core.domain.model.RoomUnreadItem;
import com.nowcoder.community.im.core.domain.repository.UserInboxRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UnreadApplicationServiceTest {

    private final UserInboxRepository userInboxRepository = mock(UserInboxRepository.class);
    private final UnreadApplicationService service = new UnreadApplicationService(userInboxRepository);

    @Test
    void summaryShouldClampLimitBelowOneUpToOne() {
        UUID viewerId = uuid(1);

        service.summary(viewerId, 0);

        verify(userInboxRepository).listRoomUnread(viewerId, 1);
        verify(userInboxRepository).listConversationUnread(viewerId, 1);
    }

    @Test
    void summaryShouldClampLimitAbove5000DownTo5000() {
        UUID viewerId = uuid(1);

        service.summary(viewerId, 6000);

        verify(userInboxRepository).listRoomUnread(viewerId, 5000);
        verify(userInboxRepository).listConversationUnread(viewerId, 5000);
    }

    @Test
    void summaryShouldMapInboxProjectionWithWatermarkAndUnreadCount() {
        UUID viewerId = uuid(1);
        UUID roomId = uuid(2);
        when(userInboxRepository.listRoomUnread(viewerId, 10))
                .thenReturn(List.of(new RoomUnreadItem(roomId, 7L, 4L, 3L)));
        when(userInboxRepository.listConversationUnread(viewerId, 10))
                .thenReturn(List.of(new ConversationUnreadItem("conversation-1", 9L, 5L, 4L)));

        UnreadSummaryResult summary = service.summary(viewerId, 10);

        assertThat(summary.rooms())
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.roomId()).isEqualTo(roomId);
                    assertThat(item.lastSeq()).isEqualTo(7L);
                    assertThat(item.lastReadSeq()).isEqualTo(4L);
                    assertThat(item.unreadCount()).isEqualTo(3L);
                });
        assertThat(summary.conversations())
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.conversationId()).isEqualTo("conversation-1");
                    assertThat(item.lastSeq()).isEqualTo(9L);
                    assertThat(item.lastReadSeq()).isEqualTo(5L);
                    assertThat(item.unreadCount()).isEqualTo(4L);
                });
    }

    private static UUID uuid(long suffix) {
        return UUID.fromString("00000000-0000-7000-8000-" + String.format("%012x", suffix));
    }
}
