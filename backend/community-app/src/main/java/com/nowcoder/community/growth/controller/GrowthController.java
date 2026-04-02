package com.nowcoder.community.growth.controller;

import com.nowcoder.community.common.web.Result;
import com.nowcoder.community.growth.dto.GrowthSummaryResponse;
import com.nowcoder.community.growth.dto.TaskCenterResponse;
import com.nowcoder.community.growth.service.GrowthBusinessTimeService;
import com.nowcoder.community.growth.service.RewardAccountService;
import com.nowcoder.community.growth.service.TaskCenterService;
import com.nowcoder.community.growth.service.UserLevelService;
import com.nowcoder.community.infra.security.auth.CurrentUser;
import com.nowcoder.community.user.api.model.UserGrowthProfileView;
import com.nowcoder.community.user.api.query.UserProfileQueryApi;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/growth")
public class GrowthController {

    private final UserProfileQueryApi userProfileQueryApi;
    private final RewardAccountService rewardAccountService;
    private final TaskCenterService taskCenterService;
    private final GrowthBusinessTimeService growthBusinessTimeService;
    private final UserLevelService userLevelService;

    public GrowthController(
            UserProfileQueryApi userProfileQueryApi,
            RewardAccountService rewardAccountService,
            TaskCenterService taskCenterService,
            GrowthBusinessTimeService growthBusinessTimeService,
            UserLevelService userLevelService
    ) {
        this.userProfileQueryApi = userProfileQueryApi;
        this.rewardAccountService = rewardAccountService;
        this.taskCenterService = taskCenterService;
        this.growthBusinessTimeService = growthBusinessTimeService;
        this.userLevelService = userLevelService;
    }

    @GetMapping("/summary")
    public Result<GrowthSummaryResponse> summary(Authentication authentication) {
        int userId = CurrentUser.requireUserId(authentication);
        UserGrowthProfileView profile = userProfileQueryApi.getGrowthProfile(userId);

        GrowthSummaryResponse resp = new GrowthSummaryResponse();
        resp.setUserId(userId);
        resp.setScore(profile.score());
        resp.setLevel(profile.level());
        UserLevelService.UserLevelSummary levelSummary = userLevelService.evaluateLevel(userId);
        resp.setUserLevel(levelSummary.userLevel());
        resp.setSignInDaysInWindow(levelSummary.signInDaysInWindow());
        resp.setWindowDays(levelSummary.windowDays());
        resp.setRewardBalance(rewardAccountService.availableBalanceOf(userId));
        resp.setFrozenBalance(rewardAccountService.frozenBalanceOf(userId));
        return Result.ok(resp);
    }

    @GetMapping("/tasks")
    public Result<TaskCenterResponse> tasks(
            Authentication authentication,
            @RequestParam(required = false) LocalDate date
    ) {
        int userId = CurrentUser.requireUserId(authentication);
        return Result.ok(taskCenterService.snapshot(userId, date == null ? growthBusinessTimeService.today() : date));
    }
}
