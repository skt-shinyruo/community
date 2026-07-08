package com.nowcoder.community.ops.application;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.outbox.OutboxEventStatus;
import com.nowcoder.community.ops.application.command.FindOutboxEventsCommand;
import com.nowcoder.community.ops.application.command.RecordGovernanceAuditCommand;
import com.nowcoder.community.ops.application.command.ReplayOutboxBatchCommand;
import com.nowcoder.community.ops.application.command.ReplayOutboxEventCommand;
import com.nowcoder.community.ops.application.result.OutboxBacklogResult;
import com.nowcoder.community.ops.application.result.OutboxEventResult;
import com.nowcoder.community.ops.domain.model.GovernanceResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxGovernanceApplicationServiceTest {

    private OutboxGovernancePort port;
    private OutboxHandlerCatalog handlerCatalog;
    private GovernanceMetrics replayMetrics;
    private GovernanceAuditPort auditPort;
    private OutboxGovernanceApplicationService service;

    @BeforeEach
    void setUp() {
        port = mock(OutboxGovernancePort.class);
        handlerCatalog = mock(OutboxHandlerCatalog.class);
        replayMetrics = mock(GovernanceMetrics.class);
        auditPort = mock(GovernanceAuditPort.class);
        service = new OutboxGovernanceApplicationService(port, handlerCatalog, replayMetrics, auditPort);
    }

    @Test
    void listBacklogShouldDelegateToPort() {
        when(port.listBacklog()).thenReturn(List.of(new OutboxBacklogResult("projection.search.post", "DEAD", 2L)));

        List<OutboxBacklogResult> result = service.listBacklog();

        assertThat(result).containsExactly(new OutboxBacklogResult("projection.search.post", "DEAD", 2L));
    }

    @Test
    void findEventsShouldNormalizeLimitAndDelegate() {
        FindOutboxEventsCommand command = new FindOutboxEventsCommand(
                OutboxEventStatus.DEAD,
                " projection.search.post ",
                null,
                Instant.parse("2026-07-07T00:00:00Z"),
                Instant.parse("2026-07-08T00:00:00Z"),
                1000
        );

        service.findEvents(command);

        verify(port).findEvents(new FindOutboxEventsCommand(
                OutboxEventStatus.DEAD,
                "projection.search.post",
                null,
                Instant.parse("2026-07-07T00:00:00Z"),
                Instant.parse("2026-07-08T00:00:00Z"),
                500
        ));
    }

    @Test
    void replayShouldRejectNonDeadEvent() {
        UUID outboxId = uuid(1);
        when(port.findById(outboxId)).thenReturn(Optional.of(event(outboxId, OutboxEventStatus.PENDING, "{}")));

        assertThatThrownBy(() -> service.replay(new ReplayOutboxEventCommand(uuid(99), outboxId, "retry after fix")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("only DEAD outbox events can be replayed");
        verify(replayMetrics).recordReplay("projection.search.post", "MANUAL_REPAIR_REQUIRED");
    }

    @Test
    void replayShouldRejectMissingHandler() {
        UUID outboxId = uuid(1);
        when(port.findById(outboxId)).thenReturn(Optional.of(event(outboxId, OutboxEventStatus.DEAD, "{}")));
        when(handlerCatalog.hasHandler("projection.search.post")).thenReturn(false);

        assertThatThrownBy(() -> service.replay(new ReplayOutboxEventCommand(uuid(99), outboxId, "retry after fix")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("no outbox handler registered");
        verify(replayMetrics).recordReplay("projection.search.post", "MANUAL_REPAIR_REQUIRED");
    }

    @Test
    void replayShouldRequeueDeadEvent() {
        UUID outboxId = uuid(1);
        UUID actorId = uuid(99);
        when(port.findById(outboxId)).thenReturn(Optional.of(event(outboxId, OutboxEventStatus.DEAD, "{\"postId\":\"p1\"}")));
        when(handlerCatalog.hasHandler("projection.search.post")).thenReturn(true);
        when(port.requeueDead(outboxId, "retry after fix")).thenReturn(true);

        var result = service.replay(new ReplayOutboxEventCommand(actorId, outboxId, "retry after fix"));

        assertThat(result.replayed()).isTrue();
        assertThat(result.beforeStatus()).isEqualTo(OutboxEventStatus.DEAD);
        assertThat(result.afterStatus()).isEqualTo(OutboxEventStatus.PENDING);
        assertThat(result.topic()).isEqualTo("projection.search.post");
        verify(port).requeueDead(outboxId, "retry after fix");
        verify(replayMetrics).recordReplay("projection.search.post", "REPLAYED");
    }

    @Test
    void replayShouldRecordNotRequeuedResultWhenStoreDoesNotRequeue() {
        UUID outboxId = uuid(1);
        UUID actorId = uuid(99);
        when(port.findById(outboxId)).thenReturn(Optional.of(event(outboxId, OutboxEventStatus.DEAD, "{\"postId\":\"p1\"}")));
        when(handlerCatalog.hasHandler("projection.search.post")).thenReturn(true);
        when(port.requeueDead(outboxId, "retry after fix")).thenReturn(false);

        var result = service.replay(new ReplayOutboxEventCommand(actorId, outboxId, "retry after fix"));

        assertThat(result.replayed()).isFalse();
        assertThat(result.result()).isEqualTo("NOT_REQUEUED");
        verify(replayMetrics).recordReplay("projection.search.post", "NOT_REQUEUED");
    }

    @Test
    void replayBatchShouldRequireBoundedDeadTopicRangeAndReason() {
        UUID actorId = uuid(99);
        Instant from = Instant.parse("2026-07-07T00:00:00Z");
        Instant to = Instant.parse("2026-07-08T00:00:00Z");

        assertThatThrownBy(() -> service.replayBatch(new ReplayOutboxBatchCommand(
                actorId, "", OutboxEventStatus.DEAD, from, to, 10, "retry")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("topic is required");
        assertThatThrownBy(() -> service.replayBatch(new ReplayOutboxBatchCommand(
                actorId, "projection.search.post", OutboxEventStatus.PENDING, from, to, 10, "retry")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("only DEAD outbox events can be batch replayed");
        assertThatThrownBy(() -> service.replayBatch(new ReplayOutboxBatchCommand(
                actorId, "projection.search.post", OutboxEventStatus.DEAD, null, to, 10, "retry")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("createdFrom and createdTo are required");
        assertThatThrownBy(() -> service.replayBatch(new ReplayOutboxBatchCommand(
                actorId, "projection.search.post", OutboxEventStatus.DEAD, to, from, 10, "retry")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("createdFrom must be before createdTo");
        assertThatThrownBy(() -> service.replayBatch(new ReplayOutboxBatchCommand(
                actorId, "projection.search.post", OutboxEventStatus.DEAD, from, to, 501, "retry")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("limit must be between 1 and 500");
        assertThatThrownBy(() -> service.replayBatch(new ReplayOutboxBatchCommand(
                actorId, "projection.search.post", OutboxEventStatus.DEAD, from, to, 10, " ")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("replay reason is required");
    }

    @Test
    void replayBatchShouldPartiallyReplayAndAuditEachRow() {
        UUID actorId = uuid(99);
        UUID replayedId = uuid(1);
        UUID nonDeadId = uuid(2);
        UUID blankPayloadId = uuid(3);
        Instant from = Instant.parse("2026-07-07T00:00:00Z");
        Instant to = Instant.parse("2026-07-08T00:00:00Z");
        when(handlerCatalog.hasHandler("projection.search.post")).thenReturn(true);
        when(port.findEvents(new FindOutboxEventsCommand(
                OutboxEventStatus.DEAD,
                "projection.search.post",
                null,
                from,
                to,
                20
        ))).thenReturn(List.of(
                event(replayedId, OutboxEventStatus.DEAD, "{\"postId\":\"p1\"}"),
                event(nonDeadId, OutboxEventStatus.PENDING, "{\"postId\":\"p2\"}"),
                event(blankPayloadId, OutboxEventStatus.DEAD, " ")
        ));
        when(port.requeueDead(replayedId, "retry after fixing handler")).thenReturn(true);

        var result = service.replayBatch(new ReplayOutboxBatchCommand(
                actorId,
                " projection.search.post ",
                OutboxEventStatus.DEAD,
                from,
                to,
                20,
                " retry after fixing handler "
        ));

        assertThat(result.topic()).isEqualTo("projection.search.post");
        assertThat(result.requestedCount()).isEqualTo(3);
        assertThat(result.replayedCount()).isEqualTo(1);
        assertThat(result.rejectedCount()).isEqualTo(2);
        assertThat(result.notRequeuedCount()).isZero();
        assertThat(result.result()).isEqualTo(GovernanceResult.PARTIAL.name());
        assertThat(result.items())
                .extracting(item -> item.outboxId() + "|" + item.result() + "|" + item.replayed())
                .containsExactly(
                        replayedId + "|REPLAYED|true",
                        nonDeadId + "|MANUAL_REPAIR_REQUIRED|false",
                        blankPayloadId + "|MANUAL_REPAIR_REQUIRED|false"
                );
        verify(port).requeueDead(replayedId, "retry after fixing handler");
        verify(replayMetrics).recordOutboxBatchReplay("projection.search.post", GovernanceResult.PARTIAL.name(), 3);
        verify(replayMetrics).recordGovernanceAction("OUTBOX_REPLAY_BATCH", GovernanceResult.PARTIAL.name());
        verify(auditPort, times(4)).record(org.mockito.ArgumentMatchers.any(RecordGovernanceAuditCommand.class));
    }

    private static OutboxEventResult event(UUID id, String status, String payload) {
        return new OutboxEventResult(
                id,
                "event-1",
                "projection.search.post",
                "post-1",
                payload,
                status,
                3,
                null,
                "boom",
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                "00-aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa-00f067aa0ba902b7-01",
                Instant.parse("2026-07-07T00:00:00Z"),
                Instant.parse("2026-07-07T00:01:00Z")
        );
    }

    private static UUID uuid(long suffix) {
        return UUID.fromString("00000000-0000-7000-8000-" + String.format("%012x", suffix));
    }
}
