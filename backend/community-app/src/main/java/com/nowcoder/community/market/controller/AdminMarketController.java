package com.nowcoder.community.market.controller;

import com.nowcoder.community.common.web.Result;
import com.nowcoder.community.infra.security.auth.CurrentUser;
import com.nowcoder.community.market.dto.AdminResolveMarketDisputeRequest;
import com.nowcoder.community.market.dto.MarketDisputeResponse;
import com.nowcoder.community.market.service.MarketDisputeService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/market/disputes")
public class AdminMarketController {

    private final MarketDisputeService marketDisputeService;

    public AdminMarketController(MarketDisputeService marketDisputeService) {
        this.marketDisputeService = marketDisputeService;
    }

    @GetMapping
    public Result<List<MarketDisputeResponse>> list(Authentication authentication) {
        CurrentUser.requireUserId(authentication);
        return Result.ok(marketDisputeService.listOpenDisputes());
    }

    @PostMapping("/{disputeId}/resolve-refund")
    public Result<MarketDisputeResponse> resolveRefund(Authentication authentication,
                                                       @PathVariable long disputeId,
                                                       @RequestBody @Valid AdminResolveMarketDisputeRequest request) {
        int actorUserId = CurrentUser.requireUserId(authentication);
        return Result.ok(marketDisputeService.adminResolveRefund(disputeId, actorUserId, request.getNote()));
    }

    @PostMapping("/{disputeId}/resolve-release")
    public Result<MarketDisputeResponse> resolveRelease(Authentication authentication,
                                                        @PathVariable long disputeId,
                                                        @RequestBody @Valid AdminResolveMarketDisputeRequest request) {
        int actorUserId = CurrentUser.requireUserId(authentication);
        return Result.ok(marketDisputeService.adminResolveRelease(disputeId, actorUserId, request.getNote()));
    }
}
