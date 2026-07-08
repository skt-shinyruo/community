package com.nowcoder.community.ops.controller;

import com.nowcoder.community.app.security.CommunitySecurityConfig;
import com.nowcoder.community.common.web.GlobalExceptionHandler;
import com.nowcoder.community.common.web.SecurityExceptionHandler;
import com.nowcoder.community.ops.application.HotCacheGovernanceApplicationService;
import com.nowcoder.community.ops.application.command.GetHotCacheStatusCommand;
import com.nowcoder.community.ops.application.command.PrewarmHotCacheCommand;
import com.nowcoder.community.ops.application.command.UpdateHotCacheDegradationCommand;
import com.nowcoder.community.ops.application.result.HotCacheDegradationSignalResult;
import com.nowcoder.community.ops.application.result.HotCachePrewarmResult;
import com.nowcoder.community.ops.application.result.HotCacheStatusResult;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
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
                .thenReturn(new HotCacheStatusResult("board", boardId, "hot-v2", 12, true, false, "", prewarmAt));

        mockMvc.perform(get("/api/ops/hot-cache/status")
                        .param("scope", "board")
                        .param("boardId", boardId.toString())
                        .with(jwt().jwt(jwt -> jwt.subject(adminUserId.toString())).authorities(() -> "ROLE_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.scope").value("board"))
                .andExpect(jsonPath("$.data.boardId").value(boardId.toString()))
                .andExpect(jsonPath("$.data.rankVersion").value("hot-v2"))
                .andExpect(jsonPath("$.data.itemCount").value(12));

        ArgumentCaptor<GetHotCacheStatusCommand> commandCaptor =
                ArgumentCaptor.forClass(GetHotCacheStatusCommand.class);
        verify(hotCacheGovernanceApplicationService).getStatus(commandCaptor.capture());
        assertEquals(boardId, commandCaptor.getValue().boardId());
    }

    @Test
    void adminShouldPrewarmWithActorFromJwt() throws Exception {
        UUID adminUserId = uuid(99);
        UUID boardId = uuid(8);
        Instant prewarmAt = Instant.parse("2026-07-07T10:00:00Z");
        when(hotCacheGovernanceApplicationService.prewarm(any()))
                .thenReturn(new HotCachePrewarmResult("board", boardId, 20, 18, 18, "hot-v2", false, "", prewarmAt));

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

        ArgumentCaptor<PrewarmHotCacheCommand> commandCaptor =
                ArgumentCaptor.forClass(PrewarmHotCacheCommand.class);
        verify(hotCacheGovernanceApplicationService).prewarm(commandCaptor.capture());
        PrewarmHotCacheCommand command = commandCaptor.getValue();
        assertAll(
                () -> assertEquals(adminUserId, command.actorUserId()),
                () -> assertEquals("board", command.scope()),
                () -> assertEquals(boardId, command.boardId()),
                () -> assertEquals(20, command.limit()),
                () -> assertEquals("warm board", command.reason())
        );
    }

    @Test
    void adminShouldQueryAndUpdateDegradationSignal() throws Exception {
        UUID adminUserId = uuid(99);
        Instant updatedAt = Instant.parse("2026-07-07T10:00:00Z");
        when(hotCacheGovernanceApplicationService.getDegradationSignal())
                .thenReturn(new HotCacheDegradationSignalResult(false, "", null));
        when(hotCacheGovernanceApplicationService.updateDegradation(any()))
                .thenReturn(new HotCacheDegradationSignalResult(true, "redis maintenance", updatedAt));

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

        ArgumentCaptor<UpdateHotCacheDegradationCommand> commandCaptor =
                ArgumentCaptor.forClass(UpdateHotCacheDegradationCommand.class);
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

    private static UUID uuid(long suffix) {
        return UUID.fromString("00000000-0000-7000-8000-" + String.format("%012x", suffix));
    }
}
