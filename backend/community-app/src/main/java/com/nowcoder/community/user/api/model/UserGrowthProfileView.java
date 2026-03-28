package com.nowcoder.community.user.api.model;

public record UserGrowthProfileView(
        int userId,
        String username,
        int score,
        int level,
        String email,
        int status,
        String headerUrl
) {
}
