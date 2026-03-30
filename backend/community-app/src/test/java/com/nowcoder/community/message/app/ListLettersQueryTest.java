package com.nowcoder.community.message.app;

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

class ListLettersQueryTest {

    @Test
    void listLettersShouldNormalizeNullPageAndSize() {
        PrivateMessageService privateMessageService = mock(PrivateMessageService.class);
        ListLettersQuery query = new ListLettersQuery(privateMessageService);
        Authentication authentication = authentication(7);
        LetterItemResponse item = new LetterItemResponse();
        item.setId(12);
        when(privateMessageService.listLetterItems(7, "1_9", 0, 10)).thenReturn(List.of(item));

        List<LetterItemResponse> result = query.listLetters(authentication, "1_9", null, null);

        assertThat(result).containsExactly(item);
        verify(privateMessageService).listLetterItems(7, "1_9", 0, 10);
    }

    @Test
    void listLettersShouldClampNegativePageAndMinimumSize() {
        PrivateMessageService privateMessageService = mock(PrivateMessageService.class);
        ListLettersQuery query = new ListLettersQuery(privateMessageService);
        Authentication authentication = authentication(7);
        LetterItemResponse item = new LetterItemResponse();
        item.setId(12);
        when(privateMessageService.listLetterItems(7, "1_9", 0, 1)).thenReturn(List.of(item));

        List<LetterItemResponse> result = query.listLetters(authentication, "1_9", -5, 0);

        assertThat(result).containsExactly(item);
        verify(privateMessageService).listLetterItems(7, "1_9", 0, 1);
    }

    @Test
    void listLettersShouldClampMaximumSize() {
        PrivateMessageService privateMessageService = mock(PrivateMessageService.class);
        ListLettersQuery query = new ListLettersQuery(privateMessageService);
        Authentication authentication = authentication(7);
        LetterItemResponse item = new LetterItemResponse();
        item.setId(12);
        when(privateMessageService.listLetterItems(7, "1_9", 3, 50)).thenReturn(List.of(item));

        List<LetterItemResponse> result = query.listLetters(authentication, "1_9", 3, 99);

        assertThat(result).containsExactly(item);
        verify(privateMessageService).listLetterItems(7, "1_9", 3, 50);
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
