package com.nowcoder.community.profile.application.result;

import java.util.Date;
import java.util.UUID;

public record UserProfilePageResult(
        UUID id,
        String username,
        String headerUrl,
        int type,
        int status,
        Date createTime,
        boolean userLevelEnabled,
        Integer userLevel,
        Integer signInDaysInWindow,
        long likeCount,
        long followeeCount,
        long followerCount
) {
}
