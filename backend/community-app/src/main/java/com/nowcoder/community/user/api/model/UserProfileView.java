package com.nowcoder.community.user.api.model;

import java.util.Date;
import java.util.UUID;

public record UserProfileView(
        UUID userId,
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
