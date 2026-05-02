package com.nowcoder.community.market.application.result;

public record MarketOrderAutoConfirmResult(
        int completedCount,
        int skippedCount
) {
}
