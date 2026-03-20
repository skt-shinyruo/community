package com.nowcoder.community.user.controller;

import com.nowcoder.community.common.web.Result;
import com.nowcoder.community.user.dto.LeaderboardItemResponse;
import com.nowcoder.community.user.service.LeaderboardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public class LeaderboardController {

    private final LeaderboardService leaderboardService;

    public LeaderboardController(LeaderboardService leaderboardService) {
        this.leaderboardService = leaderboardService;
    }

    @GetMapping("/leaderboard")
    public Result<List<LeaderboardItemResponse>> leaderboard(@RequestParam(required = false) Integer limit) {
        int l = limit == null ? 50 : Math.min(100, Math.max(1, limit));
        return Result.ok(leaderboardService.topByScore(l));
    }
}

