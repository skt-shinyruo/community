package com.nowcoder.community.message.controller;

import com.nowcoder.community.message.dto.ConversationItemResponse;
import com.nowcoder.community.message.entity.Message;
import com.nowcoder.community.message.mapper.MessageMapper;
import com.nowcoder.community.message.security.OwnerGuard;
import com.nowcoder.community.message.service.MessageItemAssembler;
import com.nowcoder.community.message.service.PrivateMessageGovernanceService;
import com.nowcoder.community.message.service.PrivateMessageService;
import com.nowcoder.community.message.service.dto.ConversationStats;
import com.nowcoder.community.user.api.model.UserSummaryView;
import com.nowcoder.community.user.api.query.UserLookupQueryApi;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

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
        UserLookupQueryApi userLookupQueryApi = mock(UserLookupQueryApi.class);
        OwnerGuard ownerGuard = mock(OwnerGuard.class);

        PrivateMessageService service = new PrivateMessageService(
                messageMapper,
                userLookupQueryApi,
                mock(PrivateMessageGovernanceService.class),
                ownerGuard,
                new MessageItemAssembler()
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
        when(userLookupQueryApi.listSummariesByIds(any())).thenReturn(List.of(
                new UserSummaryView(2, "u2", "/h2", 0),
                new UserSummaryView(3, "u3", "/h3", 0)
        ));

        List<ConversationItemResponse> items = service.listConversationItems(userId, 0, 10);

        assertThat(items).hasSize(2);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Integer>> userIdsCaptor = ArgumentCaptor.forClass(List.class);
        verify(userLookupQueryApi, times(1)).listSummariesByIds(userIdsCaptor.capture());
        assertThat(userIdsCaptor.getValue()).containsExactlyInAnyOrder(2, 3);
        verify(messageMapper, times(1)).selectConversationStats(anyInt(), any());
    }
}
