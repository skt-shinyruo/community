package com.nowcoder.community.message.controller;

import com.nowcoder.community.common.web.Result;
import com.nowcoder.community.message.dto.LetterItemResponse;
import com.nowcoder.community.message.service.NoticeService;
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
class NoticeControllerUnitTest {

    @Mock
    private NoticeService noticeService;

    private NoticeController controller;

    @BeforeEach
    void setUp() {
        controller = new NoticeController(noticeService);
    }

    @Test
    void listShouldDelegateToDtoReturningServiceMethod() {
        LetterItemResponse item = new LetterItemResponse();
        item.setId(15);
        when(noticeService.listNoticeItems(7, "comment", 0, 10)).thenReturn(List.of(item));

        Result<List<LetterItemResponse>> result = controller.list(authentication(7), "comment", null, null);

        assertThat(result.getCode()).isEqualTo(0);
        assertThat(result.getData()).containsExactly(item);
        verify(noticeService).listNoticeItems(7, "comment", 0, 10);
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
