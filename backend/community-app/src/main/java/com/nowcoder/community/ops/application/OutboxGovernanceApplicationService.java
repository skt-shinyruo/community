package com.nowcoder.community.ops.application;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.exception.CommonErrorCode;
import com.nowcoder.community.common.outbox.OutboxEventStatus;
import com.nowcoder.community.ops.application.command.FindOutboxEventsCommand;
import com.nowcoder.community.ops.application.command.RecordGovernanceAuditCommand;
import com.nowcoder.community.ops.application.command.ReplayOutboxBatchCommand;
import com.nowcoder.community.ops.application.command.ReplayOutboxEventCommand;
import com.nowcoder.community.ops.application.result.OutboxBacklogResult;
import com.nowcoder.community.ops.application.result.OutboxBatchReplayItemResult;
import com.nowcoder.community.ops.application.result.OutboxBatchReplayResult;
import com.nowcoder.community.ops.application.result.OutboxEventResult;
import com.nowcoder.community.ops.application.result.OutboxReplayResult;
import com.nowcoder.community.ops.domain.model.GovernanceAction;
import com.nowcoder.community.ops.domain.model.GovernanceResult;
import com.nowcoder.community.ops.domain.model.ReplayDecision;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
public class OutboxGovernanceApplicationService {

    private final OutboxGovernancePort outboxGovernancePort;
    private final OutboxHandlerCatalog outboxHandlerCatalog;
    private final GovernanceMetrics governanceMetrics;
    private final GovernanceAuditPort governanceAuditPort;

    public OutboxGovernanceApplicationService(
            OutboxGovernancePort outboxGovernancePort,
            OutboxHandlerCatalog outboxHandlerCatalog,
            GovernanceMetrics governanceMetrics,
            GovernanceAuditPort governanceAuditPort
    ) {
        this.outboxGovernancePort = Objects.requireNonNull(outboxGovernancePort, "outboxGovernancePort must not be null");
        this.outboxHandlerCatalog = Objects.requireNonNull(outboxHandlerCatalog, "outboxHandlerCatalog must not be null");
        this.governanceMetrics = Objects.requireNonNull(governanceMetrics, "governanceMetrics must not be null");
        this.governanceAuditPort = Objects.requireNonNull(governanceAuditPort, "governanceAuditPort must not be null");
    }

    public List<OutboxBacklogResult> listBacklog() {
        return outboxGovernancePort.listBacklog();
    }

    public List<OutboxEventResult> findEvents(FindOutboxEventsCommand command) {
        FindOutboxEventsCommand normalized = command == null
                ? new FindOutboxEventsCommand(null, null, null, null, null, 50)
                : command.normalized();
        return outboxGovernancePort.findEvents(normalized);
    }

    public OutboxReplayResult replay(ReplayOutboxEventCommand command) {
        if (command == null || command.actorUserId() == null || command.outboxId() == null) {
            throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT, "actorUserId and outboxId are required");
        }
        if (command.normalizedReason().isBlank()) {
            throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT, "replay reason is required");
        }
        OutboxEventResult event = outboxGovernancePort.findById(command.outboxId())
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND, "outbox event not found"));
        ReplayDecision decision = decideReplay(event);
        if (!decision.allowed()) {
            governanceMetrics.recordReplay(event.topic(), decision.result());
            throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT, decision.reason());
        }
        boolean requeued = outboxGovernancePort.requeueDead(command.outboxId(), command.normalizedReason());
        String result = requeued ? decision.result() : "NOT_REQUEUED";
        governanceMetrics.recordReplay(event.topic(), result);
        return new OutboxReplayResult(
                event.id(),
                event.eventId(),
                event.topic(),
                event.status(),
                requeued ? OutboxEventStatus.PENDING : event.status(),
                requeued,
                result
        );
    }

    public OutboxBatchReplayResult replayBatch(ReplayOutboxBatchCommand command) {
        ReplayOutboxBatchCommand c = validateBatchCommand(command);
        List<OutboxEventResult> events = outboxGovernancePort.findEvents(new FindOutboxEventsCommand(
                OutboxEventStatus.DEAD,
                c.topic(),
                null,
                c.createdFrom(),
                c.createdTo(),
                c.limit()
        ));
        List<OutboxBatchReplayItemResult> items = new ArrayList<>();
        int replayed = 0;
        int rejected = 0;
        int notRequeued = 0;
        for (OutboxEventResult event : events) {
            ReplayDecision decision = decideReplay(event);
            if (!decision.allowed()) {
                rejected++;
                items.add(batchItem(event, false, decision.result(), decision.reason(), event == null ? null : event.status()));
                recordBatchRowAudit(c, event, decision.result(), decision.reason());
                continue;
            }
            boolean requeued = outboxGovernancePort.requeueDead(event.id(), c.reason());
            String result = requeued ? decision.result() : GovernanceResult.NOT_REQUEUED.name();
            if (requeued) {
                replayed++;
            } else {
                notRequeued++;
            }
            items.add(batchItem(event, requeued, result, requeued ? "requeued" : "not requeued", requeued ? OutboxEventStatus.PENDING : event.status()));
            recordBatchRowAudit(c, event, result, requeued ? "requeued" : "not requeued");
        }
        String result = batchResult(replayed, rejected, notRequeued, events.size());
        OutboxBatchReplayResult batchResult = new OutboxBatchReplayResult(
                c.topic(),
                events.size(),
                replayed,
                rejected,
                notRequeued,
                result,
                items
        );
        governanceMetrics.recordOutboxBatchReplay(c.topic(), result, events.size());
        governanceMetrics.recordGovernanceAction(GovernanceAction.OUTBOX_REPLAY_BATCH.name(), result);
        recordBatchAudit(c, batchResult);
        return batchResult;
    }

    private ReplayOutboxBatchCommand validateBatchCommand(ReplayOutboxBatchCommand command) {
        if (command == null || command.actorUserId() == null) {
            throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT, "actorUserId is required");
        }
        ReplayOutboxBatchCommand c = command.normalized();
        if (c.topic() == null || c.topic().isBlank()) {
            throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT, "topic is required");
        }
        if (!OutboxEventStatus.DEAD.equals(c.status())) {
            throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT, "only DEAD outbox events can be batch replayed");
        }
        if (c.createdFrom() == null || c.createdTo() == null) {
            throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT, "createdFrom and createdTo are required");
        }
        if (c.createdFrom().isAfter(c.createdTo())) {
            throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT, "createdFrom must be before createdTo");
        }
        if (c.limit() < 1 || c.limit() > 500) {
            throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT, "limit must be between 1 and 500");
        }
        if (c.reason() == null || c.reason().isBlank()) {
            throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT, "replay reason is required");
        }
        if (!outboxHandlerCatalog.hasHandler(c.topic())) {
            throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT, "no outbox handler registered for topic=" + c.topic());
        }
        return c;
    }

    private OutboxBatchReplayItemResult batchItem(
            OutboxEventResult event,
            boolean replayed,
            String result,
            String message,
            String afterStatus
    ) {
        return new OutboxBatchReplayItemResult(
                event == null ? null : event.id(),
                event == null ? null : event.eventId(),
                event == null ? null : event.topic(),
                event == null ? null : event.status(),
                afterStatus,
                replayed,
                result,
                message
        );
    }

    private String batchResult(int replayed, int rejected, int notRequeued, int total) {
        if (total == 0 || replayed == 0 && (rejected > 0 || notRequeued > 0)) {
            return GovernanceResult.REJECTED.name();
        }
        if (rejected > 0 || notRequeued > 0 || replayed < total) {
            return GovernanceResult.PARTIAL.name();
        }
        return GovernanceResult.REPLAYED.name();
    }

    private void recordBatchAudit(ReplayOutboxBatchCommand command, OutboxBatchReplayResult result) {
        governanceAuditPort.record(new RecordGovernanceAuditCommand(
                GovernanceAction.OUTBOX_REPLAY_BATCH.name(),
                command.actorUserId(),
                "outbox_event",
                command.topic(),
                "topic=" + command.topic(),
                command.reason(),
                "{\"status\":\"" + command.status() + "\",\"limit\":" + command.limit() + "}",
                result.result(),
                "{\"requested\":" + result.requestedCount() + ",\"replayed\":" + result.replayedCount()
                        + ",\"rejected\":" + result.rejectedCount() + ",\"notRequeued\":" + result.notRequeuedCount() + "}",
                null
        ));
    }

    private void recordBatchRowAudit(ReplayOutboxBatchCommand command, OutboxEventResult event, String result, String message) {
        governanceAuditPort.record(new RecordGovernanceAuditCommand(
                GovernanceAction.OUTBOX_REPLAY_BATCH.name(),
                command.actorUserId(),
                "outbox_event",
                event == null || event.id() == null ? null : event.id().toString(),
                "topic=" + command.topic(),
                command.reason(),
                "{\"eventId\":\"" + (event == null ? "" : event.eventId()) + "\"}",
                result,
                "{\"message\":\"" + safeJson(message) + "\"}",
                event == null ? null : event.traceId()
        ));
    }

    private String safeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private ReplayDecision decideReplay(OutboxEventResult event) {
        if (event == null) {
            return ReplayDecision.reject("outbox event not found");
        }
        if (!OutboxEventStatus.DEAD.equals(event.status())) {
            return ReplayDecision.reject("only DEAD outbox events can be replayed");
        }
        if (event.topic() == null || event.topic().isBlank() || !outboxHandlerCatalog.hasHandler(event.topic())) {
            return ReplayDecision.reject("no outbox handler registered for topic=" + event.topic());
        }
        if (event.payload() == null || event.payload().isBlank()) {
            return ReplayDecision.reject("outbox payload is blank");
        }
        return ReplayDecision.allow();
    }
}
