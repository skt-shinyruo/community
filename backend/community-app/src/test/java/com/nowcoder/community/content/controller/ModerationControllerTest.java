package com.nowcoder.community.content.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.common.json.JsonMappers;
import com.nowcoder.community.common.web.Result;
import com.nowcoder.community.content.application.ModerationApplicationService;
import com.nowcoder.community.content.application.ModerationApplicationService.ModerationActionResult;
import com.nowcoder.community.content.application.ModerationApplicationService.ReportModerationResult;
import com.nowcoder.community.content.application.ModerationApplicationService.TakeModerationActionCommand;
import com.nowcoder.community.content.controller.dto.ModerationActionRequest;
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
import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ModerationControllerTest {

    private static final UUID REPORT_ID = UUID.fromString("00000000-0000-7000-8000-000000000304");
    private static final UUID ACTION_ID = UUID.fromString("00000000-0000-7000-8000-000000000305");
    private final ObjectMapper objectMapper = JsonMappers.standard();

    @Mock
    private ModerationApplicationService moderationApplicationService;

    private ModerationController controller;

    @BeforeEach
    void setUp() {
        controller = new ModerationController(moderationApplicationService);
    }

    @Test
    void reportsShouldReturnApplicationServiceProjectedResponses() {
        UUID reporterId = uuid(7);
        ReportModerationResult response = new ReportModerationResult(REPORT_ID, reporterId, 1, uuid(88), "spam", "detail", 0, new Date());
        when(moderationApplicationService.listReports(0, 1, reporterId, null, null)).thenReturn(List.of(response));

        Result<List<ReportModerationResult>> result = controller.reports(0, 1, reporterId, null, null);

        assertThat(result.getCode()).isEqualTo(0);
        assertThat(result.getData()).extracting(ReportModerationResult::id).containsExactly(REPORT_ID);
        assertThat(result.getData()).extracting(ReportModerationResult::reason).containsExactly("spam");
        assertThat(objectMapper.valueToTree(result.getData().get(0)).fieldNames()).toIterable()
                .containsExactly("id", "reporterId", "targetType", "targetId", "reason", "detail", "status", "createTime");
        verify(moderationApplicationService).listReports(0, 1, reporterId, null, null);
    }

    @Test
    void actionsShouldReturnApplicationServiceProjectedResponses() {
        UUID actorId = uuid(99);
        ModerationActionResult response = new ModerationActionResult(ACTION_ID, REPORT_ID, actorId, "ban", "abuse", 3600, new Date());
        when(moderationApplicationService.listActions(actorId, null, null)).thenReturn(List.of(response));

        Result<List<ModerationActionResult>> result = controller.actions(actorId, null, null);

        assertThat(result.getCode()).isEqualTo(0);
        assertThat(result.getData()).extracting(ModerationActionResult::id).containsExactly(ACTION_ID);
        assertThat(result.getData()).extracting(ModerationActionResult::action).containsExactly("ban");
        assertThat(objectMapper.valueToTree(result.getData().get(0)).fieldNames()).toIterable()
                .containsExactly("id", "reportId", "actorId", "action", "reason", "durationSeconds", "createTime");
        verify(moderationApplicationService).listActions(actorId, null, null);
    }

    @Test
    void actionShouldDelegateToModerationApplicationService() {
        UUID actorId = uuid(42);
        Authentication authentication = authentication(actorId);
        ModerationActionRequest request = new ModerationActionRequest(REPORT_ID, "ban", "abuse", 3600);
        TakeModerationActionCommand command = new TakeModerationActionCommand(actorId, REPORT_ID, "ban", "abuse", 3600);
        when(moderationApplicationService.takeAction(command)).thenReturn(ACTION_ID);

        Result<UUID> result = controller.action(authentication, request);

        assertThat(result.getCode()).isEqualTo(0);
        assertThat(result.getData()).isEqualTo(ACTION_ID);
        verify(moderationApplicationService).takeAction(command);
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
