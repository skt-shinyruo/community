package com.nowcoder.community.ops.application;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.outbox.OutboxEventStatus;
import com.nowcoder.community.ops.application.command.FindOutboxEventsCommand;
import com.nowcoder.community.ops.application.command.ReplayOutboxEventCommand;
import com.nowcoder.community.ops.application.result.OutboxBacklogResult;
import com.nowcoder.community.ops.application.result.OutboxEventResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxGovernanceApplicationServiceTest {

    private OutboxGovernancePort port;
    private OutboxHandlerCatalog handlerCatalog;
    private OutboxGovernanceApplicationService service;

    @BeforeEach
    void setUp() {
        port = mock(OutboxGovernancePort.class);
        handlerCatalog = mock(OutboxHandlerCatalog.class);
        service = new OutboxGovernanceApplicationService(port, handlerCatalog);
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
    }

    @Test
    void replayShouldRejectMissingHandler() {
        UUID outboxId = uuid(1);
        when(port.findById(outboxId)).thenReturn(Optional.of(event(outboxId, OutboxEventStatus.DEAD, "{}")));
        when(handlerCatalog.hasHandler("projection.search.post")).thenReturn(false);

        assertThatThrownBy(() -> service.replay(new ReplayOutboxEventCommand(uuid(99), outboxId, "retry after fix")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("no outbox handler registered");
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
