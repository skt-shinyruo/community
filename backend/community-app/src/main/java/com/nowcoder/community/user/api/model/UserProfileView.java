package com.nowcoder.community.user.api.model;

import java.util.Date;

public record UserProfileView(
        int userId,
        String username,
        String headerUrl,
        int type,
        int status,
        Date createTime,
        int score,
        int level,
        long walletBalance,
        String walletStatus
) {
}
