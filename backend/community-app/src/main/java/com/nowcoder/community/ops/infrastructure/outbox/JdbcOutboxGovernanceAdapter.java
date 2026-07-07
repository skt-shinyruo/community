package com.nowcoder.community.ops.infrastructure.outbox;

import com.nowcoder.community.common.outbox.JdbcOutboxEventStore;
import com.nowcoder.community.common.outbox.OutboxEventQuery;
import com.nowcoder.community.common.outbox.OutboxEventView;
import com.nowcoder.community.ops.application.OutboxGovernancePort;
import com.nowcoder.community.ops.application.command.FindOutboxEventsCommand;
import com.nowcoder.community.ops.application.result.OutboxBacklogResult;
import com.nowcoder.community.ops.application.result.OutboxEventResult;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcOutboxGovernanceAdapter implements OutboxGovernancePort {

    private final JdbcOutboxEventStore store;

    public JdbcOutboxGovernanceAdapter(JdbcOutboxEventStore store) {
        this.store = store;
    }

    @Override
    public List<OutboxBacklogResult> listBacklog() {
        return store.countBacklogByTopicAndStatus().stream()
                .map(row -> new OutboxBacklogResult(row.topic(), row.status(), row.count()))
                .toList();
    }

    @Override
    public List<OutboxEventResult> findEvents(FindOutboxEventsCommand command) {
        FindOutboxEventsCommand c = command == null
                ? new FindOutboxEventsCommand(null, null, null, null, null, 50)
                : command.normalized();
        return store.findEvents(new OutboxEventQuery(
                        c.status(),
                        c.topic(),
                        c.eventId(),
                        c.createdFrom(),
                        c.createdTo(),
                        c.limit()
                )).stream()
                .map(this::toResult)
                .toList();
    }

    @Override
    public Optional<OutboxEventResult> findById(UUID id) {
        return store.findEventById(id).map(this::toResult);
    }

    @Override
    public boolean requeueDead(UUID id, String reason) {
        return store.requeueDeadForReplay(id, Instant.now(), reason);
    }

    private OutboxEventResult toResult(OutboxEventView row) {
        return new OutboxEventResult(
                row.id(),
                row.eventId(),
                row.topic(),
                row.eventKey(),
                row.payload(),
                row.status(),
                row.retryCount(),
                row.nextRetryAt(),
                row.lastError(),
                row.traceId(),
                row.traceparent(),
                row.createdAt(),
                row.updatedAt()
        );
    }
}
