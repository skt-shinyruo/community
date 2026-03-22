package com.nowcoder.community.growth.controller;

import com.nowcoder.community.common.web.Result;
import com.nowcoder.community.growth.dto.GrowthSummaryResponse;
import com.nowcoder.community.growth.dto.TaskCenterResponse;
import com.nowcoder.community.growth.service.GrowthBusinessTimeService;
import com.nowcoder.community.growth.service.RewardAccountService;
import com.nowcoder.community.growth.service.TaskCenterService;
import com.nowcoder.community.infra.security.auth.CurrentUser;
import com.nowcoder.community.user.entity.User;
import com.nowcoder.community.user.service.PointsService;
import com.nowcoder.community.user.service.UserService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/growth")
public class GrowthController {

    private final UserService userService;
    private final PointsService pointsService;
    private final RewardAccountService rewardAccountService;
    private final TaskCenterService taskCenterService;
    private final GrowthBusinessTimeService growthBusinessTimeService;

    public GrowthController(
            UserService userService,
            PointsService pointsService,
            RewardAccountService rewardAccountService,
            TaskCenterService taskCenterService,
            GrowthBusinessTimeService growthBusinessTimeService
    ) {
        this.userService = userService;
        this.pointsService = pointsService;
        this.rewardAccountService = rewardAccountService;
        this.taskCenterService = taskCenterService;
        this.growthBusinessTimeService = growthBusinessTimeService;
    }

    @GetMapping("/summary")
    public Result<GrowthSummaryResponse> summary(Authentication authentication) {
        int userId = CurrentUser.requireUserId(authentication);
        User user = userService.getById(userId);

        GrowthSummaryResponse resp = new GrowthSummaryResponse();
        resp.setUserId(userId);
        resp.setScore(user.getScore());
        resp.setLevel(pointsService.levelForScore(user.getScore()));
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
