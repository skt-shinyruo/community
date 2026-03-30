package com.nowcoder.community.message.controller;

import com.nowcoder.community.common.web.Result;
import com.nowcoder.community.message.app.ListConversationItemsQuery;
import com.nowcoder.community.message.app.ListLettersQuery;
import com.nowcoder.community.message.app.MarkMessagesReadUseCase;
import com.nowcoder.community.message.app.SendPrivateMessageUseCase;
import com.nowcoder.community.message.dto.ConversationItemResponse;
import com.nowcoder.community.message.dto.LetterItemResponse;
import com.nowcoder.community.message.dto.MarkReadRequest;
import com.nowcoder.community.message.dto.SendMessageRequest;
import com.nowcoder.community.message.service.PrivateMessageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MessageControllerUnitTest {

    @Mock
    private PrivateMessageService privateMessageService;

    @Mock
    private ListConversationItemsQuery listConversationItemsQuery;

    @Mock
    private ListLettersQuery listLettersQuery;

    @Mock
    private SendPrivateMessageUseCase sendPrivateMessageUseCase;

    @Mock
    private MarkMessagesReadUseCase markMessagesReadUseCase;

    private MessageController controller;

    @BeforeEach
    void setUp() {
        controller = new MessageController(
                privateMessageService,
                listConversationItemsQuery,
                listLettersQuery,
                sendPrivateMessageUseCase,
                markMessagesReadUseCase
        );
    }

    @Test
    void conversationsShouldDelegateToConversationQuery() {
        Authentication authentication = authentication(7);
        LetterItemResponse item = new LetterItemResponse();
        item.setId(11);
        when(listConversationItemsQuery.listConversations(authentication, null, null)).thenReturn(List.of(item));

        Result<List<LetterItemResponse>> result = controller.conversations(authentication, null, null);

        assertThat(result.getCode()).isEqualTo(0);
        assertThat(result.getData()).containsExactly(item);
        verify(listConversationItemsQuery).listConversations(authentication, null, null);
    }

    @Test
    void conversationItemsShouldDelegateToConversationQuery() {
        Authentication authentication = authentication(7);
        ConversationItemResponse item = new ConversationItemResponse();
        item.setConversationId("1_7");
        when(listConversationItemsQuery.listConversationItems(authentication, 1, 20)).thenReturn(List.of(item));

        Result<List<ConversationItemResponse>> result = controller.conversationItems(authentication, 1, 20);

        assertThat(result.getCode()).isEqualTo(0);
        assertThat(result.getData()).containsExactly(item);
        verify(listConversationItemsQuery).listConversationItems(authentication, 1, 20);
    }

    @Test
    void lettersShouldDelegateToListLettersQuery() {
        Authentication authentication = authentication(7);
        LetterItemResponse item = new LetterItemResponse();
        item.setId(12);
        when(listLettersQuery.listLetters(authentication, "1_7", 1, 20)).thenReturn(List.of(item));

        Result<List<LetterItemResponse>> result = controller.letters(authentication, "1_7", 1, 20);

        assertThat(result.getCode()).isEqualTo(0);
        assertThat(result.getData()).containsExactly(item);
        verify(listLettersQuery).listLetters(authentication, "1_7", 1, 20);
    }

    @Test
    void sendShouldDelegateToUseCase() {
        Authentication authentication = authentication(7);
        SendMessageRequest request = new SendMessageRequest();
        request.setToName("alice");
        request.setContent("hello");

        Result<Void> result = controller.send(authentication, "idem-1", request);

        assertThat(result.getCode()).isEqualTo(0);
        verify(sendPrivateMessageUseCase).send(authentication, "idem-1", request);
        verifyNoInteractions(privateMessageService);
    }

    @Test
    void sendShouldDelegateToUseCaseWhenToIdProvided() {
        Authentication authentication = authentication(7);
        SendMessageRequest request = new SendMessageRequest();
        request.setToId(9);
        request.setContent("hello");

        Result<Void> result = controller.send(authentication, "idem-2", request);

        assertThat(result.getCode()).isEqualTo(0);
        verify(sendPrivateMessageUseCase).send(authentication, "idem-2", request);
        verifyNoInteractions(privateMessageService);
    }

    @Test
    void markReadShouldDelegateToUseCase() {
        Authentication authentication = authentication(7);
        MarkReadRequest request = new MarkReadRequest();
        request.setIds(List.of(1, 2));

        Result<Void> result = controller.markRead(authentication, request);

        assertThat(result.getCode()).isEqualTo(0);
        verify(markMessagesReadUseCase).markRead(authentication, request);
        verifyNoInteractions(privateMessageService);
    }

    private Authentication authentication(int userId) {
        Jwt jwt = Jwt.withTokenValue("token-" + userId)
                .header("alg", "none")
                .subject(String.valueOf(userId))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
        Authentication authentication = org.mockito.Mockito.mock(Authentication.class);
        lenient().when(authentication.getPrincipal()).thenReturn(jwt);
        return authentication;
    }
}
