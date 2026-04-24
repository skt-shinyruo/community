package com.nowcoder.community.content.controller;

import com.nowcoder.community.common.web.Result;
import com.nowcoder.community.content.dto.CreateReportRequest;
import com.nowcoder.community.content.dto.CreateReportResponse;
import com.nowcoder.community.content.service.ReportApplicationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportControllerTest {

    @Mock
    private ReportApplicationService reportApplicationService;

    private ReportController controller;

    @BeforeEach
    void setUp() {
        controller = new ReportController(reportApplicationService);
    }

    @Test
    void createShouldDelegateRawRequestFieldsToReportApplicationService() {
        UUID reporterId = uuid(7);
        UUID targetId = uuid(21);
        UUID reportId = uuid(31);
        CreateReportRequest request = new CreateReportRequest();
        request.setTargetType("post");
        request.setTargetId(targetId);
        request.setReason("spam");
        request.setDetail("burst");
        when(reportApplicationService.create(reporterId, "post", targetId, "spam", "burst"))
                .thenReturn(reportId);

        Result<CreateReportResponse> result = controller.create(authentication(reporterId), request);

        assertThat(result.getCode()).isEqualTo(0);
        assertThat(result.getData()).isNotNull();
        assertThat(result.getData().getReportId()).isEqualTo(reportId);
        verify(reportApplicationService).create(reporterId, "post", targetId, "spam", "burst");
    }

    private static Authentication authentication(UUID userId) {
        Jwt jwt = Jwt.withTokenValue("token-" + userId)
                .header("alg", "none")
                .subject(userId.toString())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
        return new TestingAuthenticationToken(jwt, null);
    }
}
