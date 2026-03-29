package com.nowcoder.community.message.controller;

import com.nowcoder.community.common.web.Result;
import com.nowcoder.community.infra.idempotency.IdempotencyGuard;
import com.nowcoder.community.message.dto.LetterItemResponse;
import com.nowcoder.community.message.service.MessageUserQueryService;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MessageControllerUnitTest {

    @Mock
    private PrivateMessageService privateMessageService;

    @Mock
    private MessageUserQueryService messageUserQueryService;

    @Mock
    private IdempotencyGuard idempotencyGuard;

    private MessageController controller;

    @BeforeEach
    void setUp() {
        controller = new MessageController(privateMessageService, messageUserQueryService, idempotencyGuard);
    }

    @Test
    void conversationsShouldDelegateToDtoReturningServiceMethod() {
        LetterItemResponse item = new LetterItemResponse();
        item.setId(11);
        when(privateMessageService.listConversationSummaries(7, 0, 10)).thenReturn(List.of(item));

        Result<List<LetterItemResponse>> result = controller.conversations(authentication(7), null, null);

        assertThat(result.getCode()).isEqualTo(0);
        assertThat(result.getData()).containsExactly(item);
        verify(privateMessageService).listConversationSummaries(7, 0, 10);
    }

    @Test
    void lettersShouldDelegateToDtoReturningServiceMethod() {
        LetterItemResponse item = new LetterItemResponse();
        item.setId(12);
        when(privateMessageService.listLetterItems(7, "1_7", 1, 20)).thenReturn(List.of(item));

        Result<List<LetterItemResponse>> result = controller.letters(authentication(7), "1_7", 1, 20);

        assertThat(result.getCode()).isEqualTo(0);
        assertThat(result.getData()).containsExactly(item);
        verify(privateMessageService).listLetterItems(7, "1_7", 1, 20);
    }

    private Authentication authentication(int userId) {
        Jwt jwt = Jwt.withTokenValue("token-" + userId)
                .header("alg", "none")
                .subject(String.valueOf(userId))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
        Authentication authentication = org.mockito.Mockito.mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(jwt);
        return authentication;
    }
}
