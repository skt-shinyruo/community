package com.nowcoder.community.growth.controller;

import com.nowcoder.community.common.web.Result;
import com.nowcoder.community.growth.dto.AdminGrowthMetricsResponse;
import com.nowcoder.community.growth.dto.AdminRewardItemUpsertRequest;
import com.nowcoder.community.growth.dto.AdminRewardOrderActionRequest;
import com.nowcoder.community.growth.entity.RewardItem;
import com.nowcoder.community.growth.entity.RewardOrder;
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
    public Result<List<RewardItem>> items() {
        return Result.ok(adminRewardOpsService.listItems());
    }

    @PostMapping("/items")
    public Result<RewardItem> upsertItem(@RequestBody AdminRewardItemUpsertRequest request) {
        return Result.ok(adminRewardOpsService.upsertItem(request));
    }

    @GetMapping("/orders")
    public Result<List<RewardOrder>> orders() {
        return Result.ok(adminRewardOpsService.listOrders());
    }

    @PostMapping("/orders/action")
    public Result<RewardOrder> processOrder(Authentication authentication, @RequestBody AdminRewardOrderActionRequest request) {
        int actorUserId = CurrentUser.requireUserId(authentication);
        return Result.ok(adminRewardOpsService.processOrder(actorUserId, request));
    }

    @GetMapping("/metrics")
    public Result<AdminGrowthMetricsResponse> metrics() {
        return Result.ok(adminRewardOpsService.metrics());
    }
}
