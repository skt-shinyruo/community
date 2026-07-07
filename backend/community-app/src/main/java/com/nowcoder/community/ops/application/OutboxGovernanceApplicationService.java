package com.nowcoder.community.ops.application;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.exception.CommonErrorCode;
import com.nowcoder.community.common.outbox.OutboxEventStatus;
import com.nowcoder.community.ops.application.command.FindOutboxEventsCommand;
import com.nowcoder.community.ops.application.command.ReplayOutboxEventCommand;
import com.nowcoder.community.ops.application.result.OutboxBacklogResult;
import com.nowcoder.community.ops.application.result.OutboxEventResult;
import com.nowcoder.community.ops.application.result.OutboxReplayResult;
import com.nowcoder.community.ops.domain.model.ReplayDecision;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
public class OutboxGovernanceApplicationService {

    private final OutboxGovernancePort outboxGovernancePort;
    private final OutboxHandlerCatalog outboxHandlerCatalog;
    private final OutboxReplayMetrics outboxReplayMetrics;

    public OutboxGovernanceApplicationService(
            OutboxGovernancePort outboxGovernancePort,
            OutboxHandlerCatalog outboxHandlerCatalog,
            OutboxReplayMetrics outboxReplayMetrics
    ) {
        this.outboxGovernancePort = Objects.requireNonNull(outboxGovernancePort, "outboxGovernancePort must not be null");
        this.outboxHandlerCatalog = Objects.requireNonNull(outboxHandlerCatalog, "outboxHandlerCatalog must not be null");
        this.outboxReplayMetrics = Objects.requireNonNull(outboxReplayMetrics, "outboxReplayMetrics must not be null");
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
            outboxReplayMetrics.recordReplay(event.topic(), decision.result());
            throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT, decision.reason());
        }
        boolean requeued = outboxGovernancePort.requeueDead(command.outboxId(), command.normalizedReason());
        String result = requeued ? decision.result() : "NOT_REQUEUED";
        outboxReplayMetrics.recordReplay(event.topic(), result);
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
