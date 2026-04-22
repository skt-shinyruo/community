package com.nowcoder.community.notice.controller;

import com.nowcoder.community.common.web.Result;
import com.nowcoder.community.notice.dto.NoticeItemResponse;
import com.nowcoder.community.notice.service.NoticeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NoticeControllerUnitTest {

    private static final UUID NOTICE_ID = UUID.fromString("00000000-0000-7000-8000-000000000421");

    @Mock
    private NoticeService noticeService;

    private NoticeController controller;

    @BeforeEach
    void setUp() {
        controller = new NoticeController(noticeService);
    }

    @Test
    void listShouldDelegateToNoticeOwnedDtoReturningServiceMethod() {
        UUID userId = uuid(7);
        NoticeItemResponse item = new NoticeItemResponse();
        item.setId(NOTICE_ID);
        item.setTopic("comment");
        when(noticeService.listNoticeItems(userId, "comment", 0, 10)).thenReturn(List.of(item));

        Result<List<NoticeItemResponse>> result = controller.list(authentication(userId), "comment", null, null);

        assertThat(result.getCode()).isEqualTo(0);
        assertThat(result.getData()).containsExactly(item);
        verify(noticeService).listNoticeItems(userId, "comment", 0, 10);
    }

    private Authentication authentication(UUID userId) {
        Jwt jwt = Jwt.withTokenValue("token-" + userId)
                .header("alg", "none")
                .subject(userId.toString())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
        Authentication authentication = org.mockito.Mockito.mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(jwt);
        return authentication;
    }
}
