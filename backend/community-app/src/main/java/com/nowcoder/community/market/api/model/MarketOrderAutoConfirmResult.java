package com.nowcoder.community.market.api.model;

public record MarketOrderAutoConfirmResult(
        int completedCount,
        int skippedCount
) {
}
