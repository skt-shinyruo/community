package com.nowcoder.community.growth.api.model;

import java.util.UUID;

public record LegacyRewardAccountView(
        UUID userId,
        int availableBalance,
        int frozenBalance
) {
}
