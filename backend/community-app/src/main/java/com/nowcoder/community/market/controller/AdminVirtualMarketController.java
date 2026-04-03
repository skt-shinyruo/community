package com.nowcoder.community.market.controller;

import com.nowcoder.community.common.web.Result;
import com.nowcoder.community.infra.security.auth.CurrentUser;
import com.nowcoder.community.market.dto.AdminResolveVirtualDisputeRequest;
import com.nowcoder.community.market.dto.VirtualDisputeResponse;
import com.nowcoder.community.market.service.VirtualDisputeService;
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
@RequestMapping("/api/admin/market/virtual/disputes")
public class AdminVirtualMarketController {

    private final VirtualDisputeService virtualDisputeService;

    public AdminVirtualMarketController(VirtualDisputeService virtualDisputeService) {
        this.virtualDisputeService = virtualDisputeService;
    }

    @GetMapping
    public Result<List<VirtualDisputeResponse>> list(Authentication authentication) {
        CurrentUser.requireUserId(authentication);
        return Result.ok(virtualDisputeService.listOpenDisputes());
    }

    @PostMapping("/{disputeId}/resolve-refund")
    public Result<VirtualDisputeResponse> resolveRefund(Authentication authentication,
                                                        @PathVariable long disputeId,
                                                        @RequestBody @Valid AdminResolveVirtualDisputeRequest request) {
        int actorUserId = CurrentUser.requireUserId(authentication);
        return Result.ok(virtualDisputeService.adminResolveRefund(disputeId, actorUserId, request.getNote()));
    }

    @PostMapping("/{disputeId}/resolve-release")
    public Result<VirtualDisputeResponse> resolveRelease(Authentication authentication,
                                                         @PathVariable long disputeId,
                                                         @RequestBody @Valid AdminResolveVirtualDisputeRequest request) {
        int actorUserId = CurrentUser.requireUserId(authentication);
        return Result.ok(virtualDisputeService.adminResolveRelease(disputeId, actorUserId, request.getNote()));
    }
}
