package com.nowcoder.community.growth.api.model;

public record LegacyRewardAccountView(
        int userId,
        int availableBalance,
        int frozenBalance
) {
}
