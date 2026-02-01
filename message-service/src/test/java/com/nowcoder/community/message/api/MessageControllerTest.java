package com.nowcoder.community.message.api;

import com.nowcoder.community.common.security.OwnerGuard;
import com.nowcoder.community.message.api.dto.ConversationItemResponse;
import com.nowcoder.community.message.dao.MessageMapper;
import com.nowcoder.community.message.entity.Message;
import com.nowcoder.community.message.projection.UserModerationProjectionRepository;
import com.nowcoder.community.message.service.PrivateMessageService;
import com.nowcoder.community.message.service.SocialServiceClient;
import com.nowcoder.community.message.service.UserModerationGuard;
import com.nowcoder.community.message.service.UserServiceClient;
import com.nowcoder.community.message.service.dto.ConversationStats;
import com.nowcoder.community.message.service.dto.UserSummary;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MessageControllerTest {

    @Test
    void conversationItemsShouldBatchUserLookupAndAvoidNPlusOne() {
        MessageMapper messageMapper = mock(MessageMapper.class);
        UserServiceClient userServiceClient = mock(UserServiceClient.class);
        SocialServiceClient socialServiceClient = mock(SocialServiceClient.class);
        UserModerationProjectionRepository projectionRepository = mock(UserModerationProjectionRepository.class);
        UserModerationGuard moderationGuard = mock(UserModerationGuard.class);
        OwnerGuard ownerGuard = mock(OwnerGuard.class);

        PrivateMessageService service = new PrivateMessageService(
                messageMapper,
                userServiceClient,
                socialServiceClient,
                projectionRepository,
                moderationGuard,
                ownerGuard
        );

        int userId = 1;
        Message m1 = new Message();
        m1.setConversationId("1_2");
        m1.setFromId(1);
        m1.setToId(2);

        Message m2 = new Message();
        m2.setConversationId("1_3");
        m2.setFromId(3);
        m2.setToId(1);

        when(messageMapper.selectConversations(anyInt(), anyInt(), anyInt())).thenReturn(List.of(m1, m2));

        ConversationStats s1 = new ConversationStats();
        s1.setConversationId("1_2");
        s1.setLetterCount(10);
        s1.setUnreadCount(1);

        ConversationStats s2 = new ConversationStats();
        s2.setConversationId("1_3");
        s2.setLetterCount(5);
        s2.setUnreadCount(0);

        when(messageMapper.selectConversationStats(anyInt(), any())).thenReturn(List.of(s1, s2));

        UserSummary u2 = new UserSummary();
        u2.setId(2);
        u2.setUsername("u2");

        UserSummary u3 = new UserSummary();
        u3.setId(3);
        u3.setUsername("u3");

        when(userServiceClient.safeBatchGetUsers(any())).thenReturn(Map.of(2, u2, 3, u3));

        List<ConversationItemResponse> items = service.listConversationItems(userId, 0, 10);
        assertThat(items).hasSize(2);

        verify(userServiceClient, times(1)).safeBatchGetUsers(Set.of(2, 3));
        verify(messageMapper, times(1)).selectConversationStats(anyInt(), any());
    }
}
