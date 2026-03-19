package com.nowcoder.community.user.service;

import com.nowcoder.community.user.dto.LeaderboardItemResponse;
import com.nowcoder.community.user.mapper.UserMapper;
import com.nowcoder.community.user.entity.User;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class LeaderboardService {

    private final UserMapper userMapper;
    private final PointsService pointsService;

    public LeaderboardService(UserMapper userMapper, PointsService pointsService) {
        this.userMapper = userMapper;
        this.pointsService = pointsService;
    }

    public List<LeaderboardItemResponse> topByScore(int limit) {
        int safeLimit = Math.min(100, Math.max(1, limit));
        List<User> users = userMapper.selectTopByScore(safeLimit);

        List<LeaderboardItemResponse> items = new ArrayList<>(users.size());
        for (int i = 0; i < users.size(); i++) {
            User u = users.get(i);
            LeaderboardItemResponse r = new LeaderboardItemResponse();
            r.setRank(i + 1);
            r.setUserId(u.getId());
            r.setUsername(u.getUsername());
            r.setHeaderUrl(u.getHeaderUrl());
            r.setScore(u.getScore());
            r.setLevel(pointsService.levelForScore(u.getScore()));
            items.add(r);
        }
        return items;
    }
}

