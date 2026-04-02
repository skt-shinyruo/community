package com.nowcoder.community.growth.controller;

import com.nowcoder.community.common.web.Result;
import com.nowcoder.community.growth.dto.AdminAdjustBalanceRequest;
import com.nowcoder.community.growth.dto.AdminRewardAdjustmentResponse;
import com.nowcoder.community.growth.dto.AdminGrowthUserResponse;
import com.nowcoder.community.growth.dto.RewardLedgerEntryResponse;
import com.nowcoder.community.growth.dto.UpdateUserLevelConfigRequest;
import com.nowcoder.community.growth.dto.UserLevelConfigResponse;
import com.nowcoder.community.growth.service.AdminGrowthService;
import com.nowcoder.community.infra.security.auth.CurrentUser;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/growth/admin")
public class AdminGrowthController {

    private final AdminGrowthService adminGrowthService;

    public AdminGrowthController(AdminGrowthService adminGrowthService) {
        this.adminGrowthService = adminGrowthService;
    }

    @GetMapping("/users/search")
    public Result<AdminGrowthUserResponse> search(
            @RequestParam(required = false) Integer userId,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String email
    ) {
        return Result.ok(adminGrowthService.search(userId, username, email));
    }

    @PostMapping("/adjustments")
    public Result<AdminGrowthUserResponse> adjust(Authentication authentication, @RequestBody AdminAdjustBalanceRequest request) {
        int actorUserId = CurrentUser.requireUserId(authentication);
        return Result.ok(adminGrowthService.adjust(actorUserId, request));
    }

    @GetMapping("/users/{userId}/ledgers")
    public Result<List<RewardLedgerEntryResponse>> ledgers(@PathVariable int userId, @RequestParam(defaultValue = "10") int limit) {
        return Result.ok(adminGrowthService.recentRewardLedgerResponses(userId, limit));
    }

    @GetMapping("/users/{userId}/adjustments")
    public Result<List<AdminRewardAdjustmentResponse>> adjustments(@PathVariable int userId, @RequestParam(defaultValue = "10") int limit) {
        return Result.ok(adminGrowthService.recentAdjustmentResponses(userId, limit));
    }

    @GetMapping("/user-level/config")
    public Result<UserLevelConfigResponse> userLevelConfig() {
        return Result.ok(adminGrowthService.getUserLevelConfig());
    }

    @PutMapping("/user-level/config")
    public Result<UserLevelConfigResponse> updateUserLevelConfig(
            Authentication authentication,
            @RequestBody UpdateUserLevelConfigRequest request
    ) {
        int actorUserId = CurrentUser.requireUserId(authentication);
        return Result.ok(adminGrowthService.updateUserLevelConfig(actorUserId, request));
    }
}
