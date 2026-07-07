package com.nowcoder.community.ops.controller;

import com.nowcoder.community.app.security.CommunitySecurityConfig;
import com.nowcoder.community.common.web.GlobalExceptionHandler;
import com.nowcoder.community.common.web.SecurityExceptionHandler;
import com.nowcoder.community.ops.application.OutboxGovernanceApplicationService;
import com.nowcoder.community.ops.application.command.FindOutboxEventsCommand;
import com.nowcoder.community.ops.application.command.ReplayOutboxEventCommand;
import com.nowcoder.community.ops.application.result.OutboxBacklogResult;
import com.nowcoder.community.ops.application.result.OutboxEventResult;
import com.nowcoder.community.ops.application.result.OutboxReplayResult;
import com.nowcoder.community.ops.security.OpsSecurityRules;
import com.nowcoder.community.support.WebMvcSliceJsonCodecTestConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.mockito.ArgumentCaptor;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OutboxOpsController.class)
@Import({
        OutboxOpsController.class,
        OpsSecurityRules.class,
        CommunitySecurityConfig.class,
        WebMvcSliceJsonCodecTestConfig.class,
        SecurityExceptionHandler.class,
        GlobalExceptionHandler.class
})
class OutboxOpsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OutboxGovernanceApplicationService outboxGovernanceApplicationService;

    @MockBean
    private JwtDecoder jwtDecoder;

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApplication {
    }

    @Test
    void nonAdminRequestsShouldBeRejected() throws Exception {
        mockMvc.perform(get("/api/ops/outbox/backlog")
                        .with(jwt().jwt(jwt -> jwt.subject(uuid(2).toString())).authorities(() -> "ROLE_USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminShouldQueryBacklogAndEventsAndReplay() throws Exception {
        UUID adminUserId = uuid(99);
        UUID outboxId = uuid(1);
        Instant createdFrom = Instant.parse("2026-07-01T00:00:00Z");
        Instant createdTo = Instant.parse("2026-07-08T00:00:00Z");
        when(outboxGovernanceApplicationService.listBacklog())
                .thenReturn(List.of(new OutboxBacklogResult("projection.search.post", "DEAD", 2L)));
        when(outboxGovernanceApplicationService.findEvents(any()))
                .thenReturn(List.of(new OutboxEventResult(
                        outboxId,
                        "event-1",
                        "projection.search.post",
                        "post-1",
                        "{\"postId\":\"post-1\"}",
                        "DEAD",
                        3,
                        null,
                        "boom",
                        "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                        "00-aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa-00f067aa0ba902b7-01",
                        Instant.parse("2026-07-07T00:00:00Z"),
                        Instant.parse("2026-07-07T00:01:00Z")
                )));
        when(outboxGovernanceApplicationService.replay(any()))
                .thenReturn(new OutboxReplayResult(outboxId, "event-1", "projection.search.post", "DEAD", "PENDING", true, "REPLAYED"));

        mockMvc.perform(get("/api/ops/outbox/backlog")
                        .with(jwt().jwt(jwt -> jwt.subject(adminUserId.toString())).authorities(() -> "ROLE_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].topic").value("projection.search.post"))
                .andExpect(jsonPath("$.data[0].status").value("DEAD"))
                .andExpect(jsonPath("$.data[0].count").value(2));

        mockMvc.perform(get("/api/ops/outbox/events")
                        .param("status", "DEAD")
                        .param("topic", "projection.search.post")
                        .param("eventId", "event-1")
                        .param("createdFrom", createdFrom.toString())
                        .param("createdTo", createdTo.toString())
                        .param("limit", "10")
                        .with(jwt().jwt(jwt -> jwt.subject(adminUserId.toString())).authorities(() -> "ROLE_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].outboxId").value(outboxId.toString()))
                .andExpect(jsonPath("$.data[0].eventId").value("event-1"));

        mockMvc.perform(post("/api/ops/outbox/events/" + outboxId + "/replay")
                        .with(jwt().jwt(jwt -> jwt.subject(adminUserId.toString())).authorities(() -> "ROLE_ADMIN"))
                        .contentType("application/json")
                        .content("{\"reason\":\"fixed es mapping\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.replayed").value(true))
                .andExpect(jsonPath("$.data.afterStatus").value("PENDING"));

        verify(outboxGovernanceApplicationService).listBacklog();
        ArgumentCaptor<FindOutboxEventsCommand> findEventsCommandCaptor =
                ArgumentCaptor.forClass(FindOutboxEventsCommand.class);
        verify(outboxGovernanceApplicationService).findEvents(findEventsCommandCaptor.capture());
        FindOutboxEventsCommand findEventsCommand = findEventsCommandCaptor.getValue();
        assertAll(
                () -> assertEquals("DEAD", findEventsCommand.status()),
                () -> assertEquals("projection.search.post", findEventsCommand.topic()),
                () -> assertEquals("event-1", findEventsCommand.eventId()),
                () -> assertEquals(createdFrom, findEventsCommand.createdFrom()),
                () -> assertEquals(createdTo, findEventsCommand.createdTo()),
                () -> assertEquals(10, findEventsCommand.limit())
        );

        ArgumentCaptor<ReplayOutboxEventCommand> replayCommandCaptor =
                ArgumentCaptor.forClass(ReplayOutboxEventCommand.class);
        verify(outboxGovernanceApplicationService, times(1)).replay(replayCommandCaptor.capture());
        ReplayOutboxEventCommand replayCommand = replayCommandCaptor.getValue();
        assertAll(
                () -> assertEquals(adminUserId, replayCommand.actorUserId()),
                () -> assertEquals(outboxId, replayCommand.outboxId()),
                () -> assertEquals("fixed es mapping", replayCommand.reason())
        );
    }

    private static UUID uuid(long suffix) {
        return UUID.fromString("00000000-0000-7000-8000-" + String.format("%012x", suffix));
    }
}
