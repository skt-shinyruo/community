package com.nowcoder.community.growth.api.model;

public record UserLevelSummaryView(
        int userLevel,
        int signInDaysInWindow,
        int windowDays,
        int lv2Threshold,
        int lv3Threshold,
        boolean enabled
) {
}
