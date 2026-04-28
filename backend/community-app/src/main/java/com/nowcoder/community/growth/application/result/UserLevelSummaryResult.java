package com.nowcoder.community.growth.application.result;

public record UserLevelSummaryResult(
        int userLevel,
        int signInDaysInWindow,
        int windowDays,
        int lv2Threshold,
        int lv3Threshold,
        boolean enabled
) {
}
