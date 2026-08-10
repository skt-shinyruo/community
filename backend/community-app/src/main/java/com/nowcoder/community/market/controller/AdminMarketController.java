package com.nowcoder.community.market.controller;

import com.nowcoder.community.common.web.Result;
import com.nowcoder.community.infra.security.auth.CurrentUser;
import com.nowcoder.community.market.controller.dto.AdminResolveMarketDisputeRequest;
import com.nowcoder.community.market.application.result.MarketDisputeResult;
import com.nowcoder.community.market.application.MarketDisputeApplicationService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/market/disputes")
public class AdminMarketController {

    private final MarketDisputeApplicationService marketDisputeApplicationService;

    public AdminMarketController(MarketDisputeApplicationService marketDisputeApplicationService) {
        this.marketDisputeApplicationService = marketDisputeApplicationService;
    }

    @GetMapping
    public Result<List<MarketDisputeResult>> list(Authentication authentication) {
        CurrentUser.requireUserUuid(authentication);
        return Result.ok(marketDisputeApplicationService.listOpenDisputes());
    }

    @PostMapping("/{disputeId}/resolve-refund")
    public Result<MarketDisputeResult> resolveRefund(Authentication authentication,
                                                       @PathVariable UUID disputeId,
                                                       @RequestBody @Valid AdminResolveMarketDisputeRequest request) {
        UUID actorUserId = CurrentUser.requireUserUuid(authentication);
        return Result.ok(marketDisputeApplicationService.adminResolveRefund(disputeId, actorUserId, request.note()));
    }

    @PostMapping("/{disputeId}/resolve-release")
    public Result<MarketDisputeResult> resolveRelease(Authentication authentication,
                                                        @PathVariable UUID disputeId,
                                                        @RequestBody @Valid AdminResolveMarketDisputeRequest request) {
        UUID actorUserId = CurrentUser.requireUserUuid(authentication);
        return Result.ok(marketDisputeApplicationService.adminResolveRelease(disputeId, actorUserId, request.note()));
    }
}
