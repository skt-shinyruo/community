package com.nowcoder.community.ops.controller;

import com.nowcoder.community.common.web.Result;
import com.nowcoder.community.infra.security.auth.CurrentUser;
import com.nowcoder.community.ops.application.HotCacheGovernanceApplicationService;
import com.nowcoder.community.ops.controller.dto.HotCacheDegradationRequest;
import com.nowcoder.community.ops.controller.dto.HotCachePrewarmRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/ops/hot-cache")
public class HotCacheOpsController {

    private final HotCacheGovernanceApplicationService hotCacheGovernanceApplicationService;

    public HotCacheOpsController(HotCacheGovernanceApplicationService hotCacheGovernanceApplicationService) {
        this.hotCacheGovernanceApplicationService = hotCacheGovernanceApplicationService;
    }

    @GetMapping("/status")
    public Result<HotCacheGovernanceApplicationService.StatusResult> status(
            @RequestParam(required = false, defaultValue = "global") String scope,
            @RequestParam(required = false) UUID boardId
    ) {
        return Result.ok(hotCacheGovernanceApplicationService.getStatus(
                new HotCacheGovernanceApplicationService.GetStatusCommand(scope, boardId)
        ));
    }

    @PostMapping("/prewarm")
    public Result<HotCacheGovernanceApplicationService.PrewarmResult> prewarm(
            Authentication authentication,
            @RequestBody @Valid HotCachePrewarmRequest request
    ) {
        UUID actorUserId = CurrentUser.requireUserUuid(authentication);
        return Result.ok(hotCacheGovernanceApplicationService.prewarm(new HotCacheGovernanceApplicationService.PrewarmCommand(
                actorUserId,
                request.getScope(),
                request.getBoardId(),
                request.getLimit(),
                request.getReason()
        )));
    }

    @GetMapping("/degradation")
    public Result<HotCacheGovernanceApplicationService.DegradationSignalResult> degradation() {
        return Result.ok(hotCacheGovernanceApplicationService.getDegradationSignal());
    }

    @PostMapping("/degradation")
    public Result<HotCacheGovernanceApplicationService.DegradationSignalResult> updateDegradation(
            Authentication authentication,
            @RequestBody @Valid HotCacheDegradationRequest request
    ) {
        UUID actorUserId = CurrentUser.requireUserUuid(authentication);
        return Result.ok(hotCacheGovernanceApplicationService.updateDegradation(
                new HotCacheGovernanceApplicationService.UpdateDegradationCommand(
                        actorUserId,
                        request.degraded(),
                        request.reason()
                )
        ));
    }
}
