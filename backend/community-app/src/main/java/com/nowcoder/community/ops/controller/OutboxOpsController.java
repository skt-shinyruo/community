package com.nowcoder.community.ops.controller;

import com.nowcoder.community.common.web.Result;
import com.nowcoder.community.infra.security.auth.CurrentUser;
import com.nowcoder.community.ops.application.OutboxGovernanceApplicationService;
import com.nowcoder.community.ops.application.command.FindOutboxEventsCommand;
import com.nowcoder.community.ops.application.command.ReplayOutboxBatchCommand;
import com.nowcoder.community.ops.application.command.ReplayOutboxEventCommand;
import com.nowcoder.community.ops.application.result.OutboxBacklogResult;
import com.nowcoder.community.ops.application.result.OutboxBatchReplayItemResult;
import com.nowcoder.community.ops.application.result.OutboxBatchReplayResult;
import com.nowcoder.community.ops.application.result.OutboxEventResult;
import com.nowcoder.community.ops.application.result.OutboxReplayResult;
import com.nowcoder.community.ops.controller.dto.OutboxBatchReplayItemResponse;
import com.nowcoder.community.ops.controller.dto.OutboxBatchReplayRequest;
import com.nowcoder.community.ops.controller.dto.OutboxBatchReplayResponse;
import com.nowcoder.community.ops.controller.dto.OutboxBacklogResponse;
import com.nowcoder.community.ops.controller.dto.OutboxEventResponse;
import com.nowcoder.community.ops.controller.dto.OutboxReplayRequest;
import com.nowcoder.community.ops.controller.dto.OutboxReplayResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/ops/outbox")
public class OutboxOpsController {

    private final OutboxGovernanceApplicationService outboxGovernanceApplicationService;

    public OutboxOpsController(OutboxGovernanceApplicationService outboxGovernanceApplicationService) {
        this.outboxGovernanceApplicationService = outboxGovernanceApplicationService;
    }

    @GetMapping("/backlog")
    public Result<List<OutboxBacklogResponse>> backlog() {
        return Result.ok(outboxGovernanceApplicationService.listBacklog().stream()
                .map(this::toBacklogResponse)
                .toList());
    }

    @GetMapping("/events")
    public Result<List<OutboxEventResponse>> events(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String topic,
            @RequestParam(required = false) String eventId,
            @RequestParam(required = false) Instant createdFrom,
            @RequestParam(required = false) Instant createdTo,
            @RequestParam(required = false, defaultValue = "50") int limit
    ) {
        return Result.ok(outboxGovernanceApplicationService.findEvents(new FindOutboxEventsCommand(
                        status,
                        topic,
                        eventId,
                        createdFrom,
                        createdTo,
                        limit
                )).stream()
                .map(this::toEventResponse)
                .toList());
    }

    @PostMapping("/events/{outboxId}/replay")
    public Result<OutboxReplayResponse> replay(
            Authentication authentication,
            @PathVariable UUID outboxId,
            @RequestBody @Valid OutboxReplayRequest request
    ) {
        UUID actorUserId = CurrentUser.requireUserUuid(authentication);
        return Result.ok(toReplayResponse(outboxGovernanceApplicationService.replay(new ReplayOutboxEventCommand(
                actorUserId,
                outboxId,
                request.getReason()
        ))));
    }

    @PostMapping("/replay-batch")
    public Result<OutboxBatchReplayResponse> replayBatch(
            Authentication authentication,
            @RequestBody @Valid OutboxBatchReplayRequest request
    ) {
        UUID actorUserId = CurrentUser.requireUserUuid(authentication);
        return Result.ok(toBatchReplayResponse(outboxGovernanceApplicationService.replayBatch(new ReplayOutboxBatchCommand(
                actorUserId,
                request.getTopic(),
                request.getStatus(),
                request.getCreatedFrom(),
                request.getCreatedTo(),
                request.getLimit(),
                request.getReason()
        ))));
    }

    private OutboxBacklogResponse toBacklogResponse(OutboxBacklogResult result) {
        return new OutboxBacklogResponse(result.topic(), result.status(), result.count());
    }

    private OutboxEventResponse toEventResponse(OutboxEventResult result) {
        return new OutboxEventResponse(
                result.id(),
                result.eventId(),
                result.topic(),
                result.eventKey(),
                result.status(),
                result.retryCount(),
                result.nextRetryAt(),
                result.lastError(),
                result.traceId(),
                result.createdAt(),
                result.updatedAt()
        );
    }

    private OutboxReplayResponse toReplayResponse(OutboxReplayResult result) {
        return new OutboxReplayResponse(
                result.outboxId(),
                result.eventId(),
                result.topic(),
                result.beforeStatus(),
                result.afterStatus(),
                result.replayed(),
                result.result()
        );
    }

    private OutboxBatchReplayResponse toBatchReplayResponse(OutboxBatchReplayResult result) {
        return new OutboxBatchReplayResponse(
                result.topic(),
                result.requestedCount(),
                result.replayedCount(),
                result.rejectedCount(),
                result.notRequeuedCount(),
                result.result(),
                result.items().stream()
                        .map(this::toBatchReplayItemResponse)
                        .toList()
        );
    }

    private OutboxBatchReplayItemResponse toBatchReplayItemResponse(OutboxBatchReplayItemResult result) {
        return new OutboxBatchReplayItemResponse(
                result.outboxId(),
                result.eventId(),
                result.topic(),
                result.beforeStatus(),
                result.afterStatus(),
                result.replayed(),
                result.result(),
                result.message()
        );
    }
}
