package com.nowcoder.community.ops.controller;

import com.nowcoder.community.common.web.Result;
import com.nowcoder.community.infra.security.auth.CurrentUser;
import com.nowcoder.community.ops.application.CompensationGovernanceApplicationService;
import com.nowcoder.community.ops.application.command.TriggerCompensationCommand;
import com.nowcoder.community.ops.application.result.CompensationTriggerResult;
import com.nowcoder.community.ops.controller.dto.CompensationTriggerResponse;
import com.nowcoder.community.ops.controller.dto.TriggerCompensationRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/ops/compensations")
public class CompensationOpsController {

    private final CompensationGovernanceApplicationService compensationGovernanceApplicationService;

    public CompensationOpsController(CompensationGovernanceApplicationService compensationGovernanceApplicationService) {
        this.compensationGovernanceApplicationService = compensationGovernanceApplicationService;
    }

    @PostMapping("/{jobName}/trigger")
    public Result<CompensationTriggerResponse> trigger(
            Authentication authentication,
            @PathVariable String jobName,
            @RequestBody @Valid TriggerCompensationRequest request
    ) {
        UUID actorUserId = CurrentUser.requireUserUuid(authentication);
        return Result.ok(toResponse(compensationGovernanceApplicationService.trigger(new TriggerCompensationCommand(
                actorUserId,
                jobName,
                request.getLimit(),
                request.getReason()
        ))));
    }

    private CompensationTriggerResponse toResponse(CompensationTriggerResult result) {
        return new CompensationTriggerResponse(
                result.jobName(),
                result.accepted(),
                result.processedCount(),
                result.repairedCount(),
                result.skippedCount(),
                result.result(),
                result.message()
        );
    }
}
