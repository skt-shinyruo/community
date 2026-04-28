package com.nowcoder.community.user.application.result;

import java.util.Date;
import java.util.UUID;

public record UserProfileResult(
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
