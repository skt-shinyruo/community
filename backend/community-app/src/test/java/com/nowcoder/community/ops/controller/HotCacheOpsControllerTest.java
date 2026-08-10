package com.nowcoder.community.ops.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.app.security.CommunitySecurityConfig;
import com.nowcoder.community.common.web.GlobalExceptionHandler;
import com.nowcoder.community.common.web.Result;
import com.nowcoder.community.common.web.SecurityExceptionHandler;
import com.nowcoder.community.ops.application.HotCacheGovernanceApplicationService;
import com.nowcoder.community.ops.application.HotCacheGovernanceApplicationService.DegradationSignalResult;
import com.nowcoder.community.ops.application.HotCacheGovernanceApplicationService.GetStatusCommand;
import com.nowcoder.community.ops.application.HotCacheGovernanceApplicationService.PrewarmCommand;
import com.nowcoder.community.ops.application.HotCacheGovernanceApplicationService.PrewarmResult;
import com.nowcoder.community.ops.application.HotCacheGovernanceApplicationService.StatusResult;
import com.nowcoder.community.ops.application.HotCacheGovernanceApplicationService.UpdateDegradationCommand;
import com.nowcoder.community.ops.controller.dto.HotCacheDegradationRequest;
import com.nowcoder.community.ops.security.OpsSecurityRules;
import com.nowcoder.community.support.WebMvcSliceJsonCodecTestConfig;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(HotCacheOpsController.class)
@Import({
        HotCacheOpsController.class,
        OpsSecurityRules.class,
        CommunitySecurityConfig.class,
        WebMvcSliceJsonCodecTestConfig.class,
        SecurityExceptionHandler.class,
        GlobalExceptionHandler.class
})
class HotCacheOpsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private HotCacheOpsController controller;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private HotCacheGovernanceApplicationService hotCacheGovernanceApplicationService;

    @MockBean
    private JwtDecoder jwtDecoder;

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApplication {
    }

    @Test
    void nonAdminRequestsShouldBeRejected() throws Exception {
        mockMvc.perform(get("/api/ops/hot-cache/status")
                        .with(jwt().jwt(jwt -> jwt.subject(uuid(2).toString())).authorities(() -> "ROLE_USER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/ops/hot-cache/prewarm")
                        .with(jwt().jwt(jwt -> jwt.subject(uuid(2).toString())).authorities(() -> "ROLE_USER"))
                        .contentType("application/json")
                        .content("{\"scope\":\"global\",\"limit\":10,\"reason\":\"warm\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminShouldQueryStatus() throws Exception {
        UUID adminUserId = uuid(99);
        UUID boardId = uuid(8);
        Instant prewarmAt = Instant.parse("2026-07-07T10:00:00Z");
        when(hotCacheGovernanceApplicationService.getStatus(any()))
                .thenReturn(new StatusResult("board", boardId, "hot-v2", 12, true, false, "", prewarmAt));

        Result<StatusResult> result = controller.status("board", boardId);
        assertThat(result.getData()).isEqualTo(
                new StatusResult("board", boardId, "hot-v2", 12, true, false, "", prewarmAt));

        mockMvc.perform(get("/api/ops/hot-cache/status")
                        .param("scope", "board")
                        .param("boardId", boardId.toString())
                        .with(jwt().jwt(jwt -> jwt.subject(adminUserId.toString())).authorities(() -> "ROLE_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.scope").value("board"))
                .andExpect(jsonPath("$.data.boardId").value(boardId.toString()))
                .andExpect(jsonPath("$.data.rankVersion").value("hot-v2"))
                .andExpect(jsonPath("$.data.itemCount").value(12));

        ArgumentCaptor<GetStatusCommand> commandCaptor =
                ArgumentCaptor.forClass(GetStatusCommand.class);
        verify(hotCacheGovernanceApplicationService, times(2)).getStatus(commandCaptor.capture());
        assertEquals(boardId, commandCaptor.getValue().boardId());
    }

    @Test
    void adminShouldPrewarmWithActorFromJwt() throws Exception {
        UUID adminUserId = uuid(99);
        UUID boardId = uuid(8);
        Instant prewarmAt = Instant.parse("2026-07-07T10:00:00Z");
        when(hotCacheGovernanceApplicationService.prewarm(any()))
                .thenReturn(new PrewarmResult("board", boardId, 20, 18, 18, "hot-v2", false, "", prewarmAt));

        mockMvc.perform(post("/api/ops/hot-cache/prewarm")
                        .with(jwt().jwt(jwt -> jwt.subject(adminUserId.toString())).authorities(() -> "ROLE_ADMIN"))
                        .contentType("application/json")
                        .content("""
                                {
                                  "scope": "board",
                                  "boardId": "%s",
                                  "limit": 20,
                                  "reason": "warm board"
                                }
                                """.formatted(boardId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.scope").value("board"))
                .andExpect(jsonPath("$.data.loadedCount").value(18))
                .andExpect(jsonPath("$.data.warmedCount").value(18));

        ArgumentCaptor<PrewarmCommand> commandCaptor =
                ArgumentCaptor.forClass(PrewarmCommand.class);
        verify(hotCacheGovernanceApplicationService).prewarm(commandCaptor.capture());
        PrewarmCommand command = commandCaptor.getValue();
        assertAll(
                () -> assertEquals(adminUserId, command.actorUserId()),
                () -> assertEquals("board", command.scope()),
                () -> assertEquals(boardId, command.boardId()),
                () -> assertEquals(20, command.limit()),
                () -> assertEquals("warm board", command.reason())
        );
    }

    @Test
    void prewarmShouldKeepDefaultGlobalScopeAndLimit() throws Exception {
        UUID adminUserId = uuid(99);
        when(hotCacheGovernanceApplicationService.prewarm(any()))
                .thenReturn(new PrewarmResult("global", null, 50, 0, 0, null, false, null, null));

        mockMvc.perform(post("/api/ops/hot-cache/prewarm")
                        .with(jwt().jwt(jwt -> jwt.subject(adminUserId.toString())).authorities(() -> "ROLE_ADMIN"))
                        .contentType("application/json")
                        .content("{\"reason\":\"warm global cache\"}"))
                .andExpect(status().isOk());

        ArgumentCaptor<PrewarmCommand> commandCaptor = ArgumentCaptor.forClass(PrewarmCommand.class);
        verify(hotCacheGovernanceApplicationService).prewarm(commandCaptor.capture());
        assertAll(
                () -> assertEquals("global", commandCaptor.getValue().scope()),
                () -> assertThat(commandCaptor.getValue().boardId()).isNull(),
                () -> assertEquals(50, commandCaptor.getValue().limit())
        );
    }

    @Test
    void adminShouldQueryAndUpdateDegradationSignal() throws Exception {
        UUID adminUserId = uuid(99);
        Instant updatedAt = Instant.parse("2026-07-07T10:00:00Z");
        when(hotCacheGovernanceApplicationService.getDegradationSignal())
                .thenReturn(new DegradationSignalResult(false, "", null));
        when(hotCacheGovernanceApplicationService.updateDegradation(any()))
                .thenReturn(new DegradationSignalResult(true, "redis maintenance", updatedAt));

        mockMvc.perform(get("/api/ops/hot-cache/degradation")
                        .with(jwt().jwt(jwt -> jwt.subject(adminUserId.toString())).authorities(() -> "ROLE_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.degraded").value(false));

        mockMvc.perform(post("/api/ops/hot-cache/degradation")
                        .with(jwt().jwt(jwt -> jwt.subject(adminUserId.toString())).authorities(() -> "ROLE_ADMIN"))
                        .contentType("application/json")
                        .content("{\"degraded\":true,\"reason\":\"redis maintenance\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.degraded").value(true))
                .andExpect(jsonPath("$.data.reason").value("redis maintenance"));

        ArgumentCaptor<UpdateDegradationCommand> commandCaptor =
                ArgumentCaptor.forClass(UpdateDegradationCommand.class);
        verify(hotCacheGovernanceApplicationService).updateDegradation(commandCaptor.capture());
        assertEquals(adminUserId, commandCaptor.getValue().actorUserId());
    }

    @Test
    void prewarmShouldRejectInvalidRequestBody() throws Exception {
        mockMvc.perform(post("/api/ops/hot-cache/prewarm")
                        .with(jwt().jwt(jwt -> jwt.subject(uuid(99).toString())).authorities(() -> "ROLE_ADMIN"))
                        .contentType("application/json")
                        .content("{\"scope\":\"global\",\"limit\":0,\"reason\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void boardPrewarmShouldRejectMissingBoardId() throws Exception {
        mockMvc.perform(post("/api/ops/hot-cache/prewarm")
                        .with(jwt().jwt(jwt -> jwt.subject(uuid(99).toString())).authorities(() -> "ROLE_ADMIN"))
                        .contentType("application/json")
                        .content("{\"scope\":\"board\",\"limit\":10,\"reason\":\"warm\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void degradationRequestRecordShouldKeepUnknownFieldTolerance() throws Exception {
        HotCacheDegradationRequest request = objectMapper.readValue(
                "{\"degraded\":true,\"reason\":\"maintenance\",\"unexpected\":true}",
                HotCacheDegradationRequest.class
        );
        HotCacheDegradationRequest defaulted = objectMapper.readValue(
                "{\"reason\":\"maintenance\"}",
                HotCacheDegradationRequest.class
        );

        assertThat(request).isEqualTo(new HotCacheDegradationRequest(true, "maintenance"));
        assertThat(defaulted).isEqualTo(new HotCacheDegradationRequest(false, "maintenance"));
    }

    @Test
    void applicationResultsShouldPreserveHotCacheJsonFields() {
        UUID boardId = uuid(8);
        Instant at = Instant.parse("2026-07-07T10:00:00Z");

        var statusJson = objectMapper.valueToTree(
                new StatusResult("board", boardId, "hot-v2", 12, true, false, "", at)
        );
        var prewarmJson = objectMapper.valueToTree(
                new PrewarmResult("board", boardId, 20, 18, 18, "hot-v2", false, "", at)
        );
        var degradationJson = objectMapper.valueToTree(
                new DegradationSignalResult(true, "redis maintenance", at)
        );

        assertThat(statusJson.fieldNames()).toIterable().containsExactly(
                "scope", "boardId", "rankVersion", "itemCount", "summaryCacheAvailable",
                "degraded", "degradedReason", "lastPrewarmAt"
        );
        assertThat(prewarmJson.fieldNames()).toIterable().containsExactly(
                "scope", "boardId", "requestedCount", "loadedCount", "warmedCount",
                "rankVersion", "degraded", "degradedReason", "lastPrewarmAt"
        );
        assertThat(degradationJson.fieldNames()).toIterable().containsExactly("degraded", "reason", "updatedAt");
    }

    private static UUID uuid(long suffix) {
        return UUID.fromString("00000000-0000-7000-8000-" + String.format("%012x", suffix));
    }
}
