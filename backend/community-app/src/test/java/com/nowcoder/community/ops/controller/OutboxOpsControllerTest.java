package com.nowcoder.community.ops.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.app.security.CommunitySecurityConfig;
import com.nowcoder.community.common.web.GlobalExceptionHandler;
import com.nowcoder.community.common.web.Result;
import com.nowcoder.community.common.web.SecurityExceptionHandler;
import com.nowcoder.community.ops.application.OutboxGovernanceApplicationService;
import com.nowcoder.community.ops.application.OutboxGovernanceApplicationService.BatchReplayResult;
import com.nowcoder.community.ops.application.OutboxGovernanceApplicationService.BatchReplayResult.Item;
import com.nowcoder.community.ops.application.OutboxGovernanceApplicationService.ReplayBatchCommand;
import com.nowcoder.community.ops.application.OutboxGovernanceApplicationService.ReplayCommand;
import com.nowcoder.community.ops.application.OutboxGovernanceApplicationService.ReplayResult;
import com.nowcoder.community.ops.application.command.FindOutboxEventsCommand;
import com.nowcoder.community.ops.application.result.OutboxBacklogResult;
import com.nowcoder.community.ops.application.result.OutboxEventResult;
import com.nowcoder.community.ops.controller.dto.OutboxEventResponse;
import com.nowcoder.community.ops.controller.dto.OutboxReplayRequest;
import com.nowcoder.community.ops.security.OpsSecurityRules;
import com.nowcoder.community.support.WebMvcSliceJsonCodecTestConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.mockito.ArgumentCaptor;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.assertj.core.api.Assertions.assertThat;
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

    @Autowired
    private OutboxOpsController controller;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private OutboxGovernanceApplicationService outboxGovernanceApplicationService;

    @MockitoBean
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
        mockMvc.perform(post("/api/ops/outbox/replay-batch")
                        .with(jwt().jwt(jwt -> jwt.subject(uuid(2).toString())).authorities(() -> "ROLE_USER"))
                        .contentType("application/json")
                        .content("{\"topic\":\"eventbus.content\",\"status\":\"DEAD\",\"createdFrom\":\"2026-07-07T00:00:00Z\",\"createdTo\":\"2026-07-08T00:00:00Z\",\"limit\":10,\"reason\":\"retry\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminShouldQueryBacklogAndEventsAndReplay() throws Exception {
        UUID adminUserId = uuid(99);
        UUID outboxId = uuid(1);
        Instant createdFrom = Instant.parse("2026-07-01T00:00:00Z");
        Instant createdTo = Instant.parse("2026-07-08T00:00:00Z");
        when(outboxGovernanceApplicationService.listBacklog())
                .thenReturn(List.of(new OutboxBacklogResult("eventbus.content", "DEAD", 2L)));
        when(outboxGovernanceApplicationService.findEvents(any()))
                .thenReturn(List.of(new OutboxEventResult(
                        outboxId,
                        "event-1",
                        "eventbus.content",
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
                .thenReturn(new ReplayResult(outboxId, "event-1", "eventbus.content", "DEAD", "PENDING", true, "REPLAYED"));

        Result<List<OutboxBacklogResult>> backlog = controller.backlog();
        assertThat(backlog.getData()).containsExactly(new OutboxBacklogResult("eventbus.content", "DEAD", 2L));

        mockMvc.perform(get("/api/ops/outbox/backlog")
                        .with(jwt().jwt(jwt -> jwt.subject(adminUserId.toString())).authorities(() -> "ROLE_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].topic").value("eventbus.content"))
                .andExpect(jsonPath("$.data[0].status").value("DEAD"))
                .andExpect(jsonPath("$.data[0].count").value(2));

        mockMvc.perform(get("/api/ops/outbox/events")
                        .param("status", "DEAD")
                        .param("topic", "eventbus.content")
                        .param("eventId", "event-1")
                        .param("createdFrom", createdFrom.toString())
                        .param("createdTo", createdTo.toString())
                        .param("limit", "10")
                        .with(jwt().jwt(jwt -> jwt.subject(adminUserId.toString())).authorities(() -> "ROLE_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].outboxId").value(outboxId.toString()))
                .andExpect(jsonPath("$.data[0].eventId").value("event-1"))
                .andExpect(jsonPath("$.data[0].id").doesNotExist())
                .andExpect(jsonPath("$.data[0].payload").doesNotExist())
                .andExpect(jsonPath("$.data[0].traceparent").doesNotExist());

        mockMvc.perform(post("/api/ops/outbox/events/" + outboxId + "/replay")
                        .with(jwt().jwt(jwt -> jwt.subject(adminUserId.toString())).authorities(() -> "ROLE_ADMIN"))
                        .contentType("application/json")
                        .content("{\"reason\":\"fixed es mapping\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.replayed").value(true))
                .andExpect(jsonPath("$.data.afterStatus").value("PENDING"));

        verify(outboxGovernanceApplicationService, times(2)).listBacklog();
        ArgumentCaptor<FindOutboxEventsCommand> findEventsCommandCaptor =
                ArgumentCaptor.forClass(FindOutboxEventsCommand.class);
        verify(outboxGovernanceApplicationService).findEvents(findEventsCommandCaptor.capture());
        FindOutboxEventsCommand findEventsCommand = findEventsCommandCaptor.getValue();
        assertAll(
                () -> assertEquals("DEAD", findEventsCommand.status()),
                () -> assertEquals("eventbus.content", findEventsCommand.topic()),
                () -> assertEquals("event-1", findEventsCommand.eventId()),
                () -> assertEquals(createdFrom, findEventsCommand.createdFrom()),
                () -> assertEquals(createdTo, findEventsCommand.createdTo()),
                () -> assertEquals(10, findEventsCommand.limit())
        );

        ArgumentCaptor<ReplayCommand> replayCommandCaptor =
                ArgumentCaptor.forClass(ReplayCommand.class);
        verify(outboxGovernanceApplicationService, times(1)).replay(replayCommandCaptor.capture());
        ReplayCommand replayCommand = replayCommandCaptor.getValue();
        assertAll(
                () -> assertEquals(adminUserId, replayCommand.actorUserId()),
                () -> assertEquals(outboxId, replayCommand.outboxId()),
                () -> assertEquals("fixed es mapping", replayCommand.reason())
        );
    }

    @Test
    void adminShouldBatchReplayWithBoundedScope() throws Exception {
        UUID adminUserId = uuid(99);
        UUID replayedId = uuid(1);
        UUID rejectedId = uuid(2);
        when(outboxGovernanceApplicationService.replayBatch(any()))
                .thenReturn(new BatchReplayResult(
                        "eventbus.content",
                        2,
                        1,
                        1,
                        0,
                        "PARTIAL",
                        List.of(
                                new Item(replayedId, "event-1", "eventbus.content", "DEAD", "PENDING", true, "REPLAYED", "requeued"),
                                new Item(rejectedId, "event-2", "eventbus.content", "PENDING", "PENDING", false, "MANUAL_REPAIR_REQUIRED", "only DEAD outbox events can be replayed")
                        )
                ));

        mockMvc.perform(post("/api/ops/outbox/replay-batch")
                        .with(jwt().jwt(jwt -> jwt.subject(adminUserId.toString())).authorities(() -> "ROLE_ADMIN"))
                        .contentType("application/json")
                        .content("""
                                {
                                  "topic": "eventbus.content",
                                  "status": "DEAD",
                                  "createdFrom": "2026-07-07T00:00:00Z",
                                  "createdTo": "2026-07-08T00:00:00Z",
                                  "limit": 20,
                                  "reason": "fixed handler"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.topic").value("eventbus.content"))
                .andExpect(jsonPath("$.data.requestedCount").value(2))
                .andExpect(jsonPath("$.data.replayedCount").value(1))
                .andExpect(jsonPath("$.data.rejectedCount").value(1))
                .andExpect(jsonPath("$.data.result").value("PARTIAL"))
                .andExpect(jsonPath("$.data.items[0].outboxId").value(replayedId.toString()))
                .andExpect(jsonPath("$.data.items[0].replayed").value(true))
                .andExpect(jsonPath("$.data.items[1].result").value("MANUAL_REPAIR_REQUIRED"));

        ArgumentCaptor<ReplayBatchCommand> commandCaptor =
                ArgumentCaptor.forClass(ReplayBatchCommand.class);
        verify(outboxGovernanceApplicationService).replayBatch(commandCaptor.capture());
        ReplayBatchCommand command = commandCaptor.getValue();
        assertAll(
                () -> assertEquals(adminUserId, command.actorUserId()),
                () -> assertEquals("eventbus.content", command.topic()),
                () -> assertEquals("DEAD", command.status()),
                () -> assertEquals(Instant.parse("2026-07-07T00:00:00Z"), command.createdFrom()),
                () -> assertEquals(Instant.parse("2026-07-08T00:00:00Z"), command.createdTo()),
                () -> assertEquals(20, command.limit()),
                () -> assertEquals("fixed handler", command.reason())
        );
    }

    @Test
    void batchReplayShouldKeepDefaultLimit() throws Exception {
        UUID adminUserId = uuid(99);
        when(outboxGovernanceApplicationService.replayBatch(any()))
                .thenReturn(new BatchReplayResult("eventbus.content", 0, 0, 0, 0, "REJECTED", List.of()));

        mockMvc.perform(post("/api/ops/outbox/replay-batch")
                        .with(jwt().jwt(jwt -> jwt.subject(adminUserId.toString())).authorities(() -> "ROLE_ADMIN"))
                        .contentType("application/json")
                        .content("""
                                {
                                  "topic": "eventbus.content",
                                  "status": "DEAD",
                                  "createdFrom": "2026-07-07T00:00:00Z",
                                  "createdTo": "2026-07-08T00:00:00Z",
                                  "reason": "fixed handler"
                                }
                                """))
                .andExpect(status().isOk());

        ArgumentCaptor<ReplayBatchCommand> commandCaptor = ArgumentCaptor.forClass(ReplayBatchCommand.class);
        verify(outboxGovernanceApplicationService).replayBatch(commandCaptor.capture());
        assertEquals(50, commandCaptor.getValue().limit());
    }

    @Test
    void batchReplayShouldRejectInvalidRequestBody() throws Exception {
        mockMvc.perform(post("/api/ops/outbox/replay-batch")
                        .with(jwt().jwt(jwt -> jwt.subject(uuid(99).toString())).authorities(() -> "ROLE_ADMIN"))
                        .contentType("application/json")
                        .content("{\"topic\":\"\",\"status\":\"DEAD\",\"limit\":0,\"reason\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void replayRequestRecordShouldKeepUnknownFieldTolerance() throws Exception {
        OutboxReplayRequest request = objectMapper.readValue(
                "{\"reason\":\"fixed\",\"unexpected\":true}",
                OutboxReplayRequest.class
        );

        assertThat(request).isEqualTo(new OutboxReplayRequest("fixed"));
    }

    @Test
    void outboxEventJsonShouldExposeOnlyReviewedOperatorFields() {
        OutboxEventResponse response = new OutboxEventResponse(
                uuid(1),
                "event-1",
                "eventbus.content",
                "post-1",
                "DEAD",
                3,
                Instant.parse("2026-07-07T00:02:00Z"),
                "boom",
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                Instant.parse("2026-07-07T00:00:00Z"),
                Instant.parse("2026-07-07T00:01:00Z")
        );

        var json = objectMapper.valueToTree(response);

        assertThat(json.fieldNames()).toIterable().containsExactly(
                "outboxId",
                "eventId",
                "topic",
                "eventKey",
                "status",
                "retryCount",
                "nextRetryAt",
                "lastError",
                "traceId",
                "createdAt",
                "updatedAt"
        );
        assertThat(json.has("id")).isFalse();
        assertThat(json.has("payload")).isFalse();
        assertThat(json.has("traceparent")).isFalse();
    }

    @Test
    void applicationResultsShouldPreserveReplayJsonFields() {
        Item item = new Item(
                uuid(1), "event-1", "eventbus.content", "DEAD", "PENDING", true, "REPLAYED", "requeued"
        );

        var replayJson = objectMapper.valueToTree(
                new ReplayResult(uuid(1), "event-1", "eventbus.content", "DEAD", "PENDING", true, "REPLAYED")
        );
        var batchJson = objectMapper.valueToTree(
                new BatchReplayResult("eventbus.content", 1, 1, 0, 0, "REPLAYED", List.of(item))
        );
        var itemJson = objectMapper.valueToTree(item);

        assertThat(replayJson.fieldNames()).toIterable().containsExactly(
                "outboxId", "eventId", "topic", "beforeStatus", "afterStatus", "replayed", "result"
        );
        assertThat(batchJson.fieldNames()).toIterable().containsExactly(
                "topic", "requestedCount", "replayedCount", "rejectedCount", "notRequeuedCount", "result", "items"
        );
        assertThat(itemJson.fieldNames()).toIterable().containsExactly(
                "outboxId", "eventId", "topic", "beforeStatus", "afterStatus", "replayed", "result", "message"
        );
    }

    private static UUID uuid(long suffix) {
        return UUID.fromString("00000000-0000-7000-8000-" + String.format("%012x", suffix));
    }
}
