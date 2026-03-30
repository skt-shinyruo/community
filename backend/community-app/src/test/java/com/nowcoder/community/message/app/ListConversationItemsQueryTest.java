package com.nowcoder.community.message.app;

import com.nowcoder.community.message.dto.ConversationItemResponse;
import com.nowcoder.community.message.dto.LetterItemResponse;
import com.nowcoder.community.message.service.PrivateMessageService;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ListConversationItemsQueryTest {

    @Test
    void listConversationsShouldNormalizeNullPageAndSize() {
        PrivateMessageService privateMessageService = mock(PrivateMessageService.class);
        ListConversationItemsQuery query = new ListConversationItemsQuery(privateMessageService);
        Authentication authentication = authentication(7);
        LetterItemResponse item = new LetterItemResponse();
        item.setId(11);
        when(privateMessageService.listConversationSummaries(7, 0, 10)).thenReturn(List.of(item));

        List<LetterItemResponse> result = query.listConversations(authentication, null, null);

        assertThat(result).containsExactly(item);
        verify(privateMessageService).listConversationSummaries(7, 0, 10);
    }

    @Test
    void listConversationItemsShouldClampNegativePageAndMinimumSize() {
        PrivateMessageService privateMessageService = mock(PrivateMessageService.class);
        ListConversationItemsQuery query = new ListConversationItemsQuery(privateMessageService);
        Authentication authentication = authentication(7);
        ConversationItemResponse item = new ConversationItemResponse();
        item.setConversationId("1_7");
        when(privateMessageService.listConversationItems(7, 0, 1)).thenReturn(List.of(item));

        List<ConversationItemResponse> result = query.listConversationItems(authentication, -3, 0);

        assertThat(result).containsExactly(item);
        verify(privateMessageService).listConversationItems(7, 0, 1);
    }

    @Test
    void listConversationItemsShouldClampMaximumSize() {
        PrivateMessageService privateMessageService = mock(PrivateMessageService.class);
        ListConversationItemsQuery query = new ListConversationItemsQuery(privateMessageService);
        Authentication authentication = authentication(7);
        ConversationItemResponse item = new ConversationItemResponse();
        item.setConversationId("1_7");
        when(privateMessageService.listConversationItems(7, 2, 50)).thenReturn(List.of(item));

        List<ConversationItemResponse> result = query.listConversationItems(authentication, 2, 99);

        assertThat(result).containsExactly(item);
        verify(privateMessageService).listConversationItems(7, 2, 50);
    }

    private Authentication authentication(int userId) {
        Jwt jwt = Jwt.withTokenValue("token-" + userId)
                .header("alg", "none")
                .subject(String.valueOf(userId))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(jwt);
        return authentication;
    }
}
