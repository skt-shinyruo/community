package com.nowcoder.community.market.controller;

import com.nowcoder.community.common.web.Result;
import com.nowcoder.community.infra.security.auth.CurrentUser;
import com.nowcoder.community.market.controller.dto.AdminResolveMarketDisputeRequest;
import com.nowcoder.community.market.controller.dto.MarketDisputeResponse;
import com.nowcoder.community.market.application.result.MarketDisputeResult;
import com.nowcoder.community.market.application.AdminMarketApplicationService;
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

    private final AdminMarketApplicationService adminMarketApplicationService;

    public AdminMarketController(AdminMarketApplicationService adminMarketApplicationService) {
        this.adminMarketApplicationService = adminMarketApplicationService;
    }

    private static List<MarketDisputeResponse> toDisputeResponses(List<MarketDisputeResult> disputes) {
        return disputes.stream()
                .map(MarketDisputeResponse::from)
                .toList();
    }

    @GetMapping
    public Result<List<MarketDisputeResponse>> list(Authentication authentication) {
        CurrentUser.requireUserUuid(authentication);
        return Result.ok(toDisputeResponses(adminMarketApplicationService.listOpenDisputes()));
    }

    @PostMapping("/{disputeId}/resolve-refund")
    public Result<MarketDisputeResponse> resolveRefund(Authentication authentication,
                                                       @PathVariable UUID disputeId,
                                                       @RequestBody @Valid AdminResolveMarketDisputeRequest request) {
        UUID actorUserId = CurrentUser.requireUserUuid(authentication);
        return Result.ok(MarketDisputeResponse.from(adminMarketApplicationService.resolveRefund(disputeId, actorUserId, request.getNote())));
    }

    @PostMapping("/{disputeId}/resolve-release")
    public Result<MarketDisputeResponse> resolveRelease(Authentication authentication,
                                                        @PathVariable UUID disputeId,
                                                        @RequestBody @Valid AdminResolveMarketDisputeRequest request) {
        UUID actorUserId = CurrentUser.requireUserUuid(authentication);
        return Result.ok(MarketDisputeResponse.from(adminMarketApplicationService.resolveRelease(disputeId, actorUserId, request.getNote())));
    }
}
