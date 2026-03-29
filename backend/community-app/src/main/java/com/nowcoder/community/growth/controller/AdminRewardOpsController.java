package com.nowcoder.community.growth.controller;

import com.nowcoder.community.common.web.Result;
import com.nowcoder.community.growth.dto.AdminGrowthMetricsResponse;
import com.nowcoder.community.growth.dto.AdminRewardItemResponse;
import com.nowcoder.community.growth.dto.AdminRewardItemUpsertRequest;
import com.nowcoder.community.growth.dto.AdminRewardOrderResponse;
import com.nowcoder.community.growth.dto.AdminRewardOrderActionRequest;
import com.nowcoder.community.growth.service.AdminRewardOpsService;
import com.nowcoder.community.infra.security.auth.CurrentUser;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/growth/admin/rewards")
public class AdminRewardOpsController {

    private final AdminRewardOpsService adminRewardOpsService;

    public AdminRewardOpsController(AdminRewardOpsService adminRewardOpsService) {
        this.adminRewardOpsService = adminRewardOpsService;
    }

    @GetMapping("/items")
    public Result<List<AdminRewardItemResponse>> items() {
        return Result.ok(adminRewardOpsService.listItemResponses());
    }

    @PostMapping("/items")
    public Result<AdminRewardItemResponse> upsertItem(@RequestBody AdminRewardItemUpsertRequest request) {
        return Result.ok(adminRewardOpsService.upsertItemResponse(request));
    }

    @GetMapping("/orders")
    public Result<List<AdminRewardOrderResponse>> orders() {
        return Result.ok(adminRewardOpsService.listOrderResponses());
    }

    @PostMapping("/orders/action")
    public Result<AdminRewardOrderResponse> processOrder(Authentication authentication, @RequestBody AdminRewardOrderActionRequest request) {
        int actorUserId = CurrentUser.requireUserId(authentication);
        return Result.ok(adminRewardOpsService.processOrderResponse(actorUserId, request));
    }

    @GetMapping("/metrics")
    public Result<AdminGrowthMetricsResponse> metrics() {
        return Result.ok(adminRewardOpsService.metrics());
    }
}
