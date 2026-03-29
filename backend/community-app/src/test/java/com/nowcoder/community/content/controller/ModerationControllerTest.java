package com.nowcoder.community.content.controller;

import com.nowcoder.community.common.web.Result;
import com.nowcoder.community.content.dto.ModerationActionResponse;
import com.nowcoder.community.content.dto.ReportResponse;
import com.nowcoder.community.content.service.ModerationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ModerationControllerTest {

    @Mock
    private ModerationService moderationService;

    private ModerationController controller;

    @BeforeEach
    void setUp() {
        controller = new ModerationController(moderationService);
    }

    @Test
    void reportsShouldReturnServiceProjectedResponses() {
        ReportResponse response = new ReportResponse();
        response.setId(12);
        response.setReason("spam");
        response.setCreateTime(new Date());
        when(moderationService.listReportResponses(0, 1, 7, 0, 20)).thenReturn(List.of(response));

        Result<List<ReportResponse>> result = controller.reports(0, 1, 7, null, null);

        assertThat(result.getCode()).isEqualTo(0);
        assertThat(result.getData()).containsExactly(response);
        verify(moderationService).listReportResponses(0, 1, 7, 0, 20);
    }

    @Test
    void actionsShouldReturnServiceProjectedResponses() {
        ModerationActionResponse response = new ModerationActionResponse();
        response.setId(21);
        response.setAction("ban");
        response.setCreateTime(new Date());
        when(moderationService.listModerationActionResponses(99, 0, 20)).thenReturn(List.of(response));

        Result<List<ModerationActionResponse>> result = controller.actions(99, null, null);

        assertThat(result.getCode()).isEqualTo(0);
        assertThat(result.getData()).containsExactly(response);
        verify(moderationService).listModerationActionResponses(99, 0, 20);
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
