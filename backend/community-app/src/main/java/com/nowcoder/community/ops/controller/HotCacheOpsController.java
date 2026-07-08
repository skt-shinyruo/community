package com.nowcoder.community.ops.controller;

import com.nowcoder.community.common.web.Result;
import com.nowcoder.community.infra.security.auth.CurrentUser;
import com.nowcoder.community.ops.application.HotCacheGovernanceApplicationService;
import com.nowcoder.community.ops.application.command.GetHotCacheStatusCommand;
import com.nowcoder.community.ops.application.command.PrewarmHotCacheCommand;
import com.nowcoder.community.ops.application.command.UpdateHotCacheDegradationCommand;
import com.nowcoder.community.ops.application.result.HotCacheDegradationSignalResult;
import com.nowcoder.community.ops.application.result.HotCachePrewarmResult;
import com.nowcoder.community.ops.application.result.HotCacheStatusResult;
import com.nowcoder.community.ops.controller.dto.HotCacheDegradationRequest;
import com.nowcoder.community.ops.controller.dto.HotCacheDegradationResponse;
import com.nowcoder.community.ops.controller.dto.HotCachePrewarmRequest;
import com.nowcoder.community.ops.controller.dto.HotCachePrewarmResponse;
import com.nowcoder.community.ops.controller.dto.HotCacheStatusResponse;
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
    public Result<HotCacheStatusResponse> status(
            @RequestParam(required = false, defaultValue = "global") String scope,
            @RequestParam(required = false) UUID boardId
    ) {
        return Result.ok(toStatusResponse(hotCacheGovernanceApplicationService.getStatus(
                new GetHotCacheStatusCommand(scope, boardId)
        )));
    }

    @PostMapping("/prewarm")
    public Result<HotCachePrewarmResponse> prewarm(
            Authentication authentication,
            @RequestBody @Valid HotCachePrewarmRequest request
    ) {
        UUID actorUserId = CurrentUser.requireUserUuid(authentication);
        return Result.ok(toPrewarmResponse(hotCacheGovernanceApplicationService.prewarm(new PrewarmHotCacheCommand(
                actorUserId,
                request.getScope(),
                request.getBoardId(),
                request.getLimit(),
                request.getReason()
        ))));
    }

    @GetMapping("/degradation")
    public Result<HotCacheDegradationResponse> degradation() {
        return Result.ok(toDegradationResponse(hotCacheGovernanceApplicationService.getDegradationSignal()));
    }

    @PostMapping("/degradation")
    public Result<HotCacheDegradationResponse> updateDegradation(
            Authentication authentication,
            @RequestBody @Valid HotCacheDegradationRequest request
    ) {
        UUID actorUserId = CurrentUser.requireUserUuid(authentication);
        return Result.ok(toDegradationResponse(hotCacheGovernanceApplicationService.updateDegradation(
                new UpdateHotCacheDegradationCommand(actorUserId, request.isDegraded(), request.getReason())
        )));
    }

    private HotCacheStatusResponse toStatusResponse(HotCacheStatusResult result) {
        return new HotCacheStatusResponse(
                result.scope(),
                result.boardId(),
                result.rankVersion(),
                result.itemCount(),
                result.summaryCacheAvailable(),
                result.degraded(),
                result.degradedReason(),
                result.lastPrewarmAt()
        );
    }

    private HotCachePrewarmResponse toPrewarmResponse(HotCachePrewarmResult result) {
        return new HotCachePrewarmResponse(
                result.scope(),
                result.boardId(),
                result.requestedCount(),
                result.loadedCount(),
                result.warmedCount(),
                result.rankVersion(),
                result.degraded(),
                result.degradedReason(),
                result.lastPrewarmAt()
        );
    }

    private HotCacheDegradationResponse toDegradationResponse(HotCacheDegradationSignalResult result) {
        return new HotCacheDegradationResponse(result.degraded(), result.reason(), result.updatedAt());
    }
}
