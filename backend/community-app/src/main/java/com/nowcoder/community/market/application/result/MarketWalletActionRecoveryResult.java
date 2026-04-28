package com.nowcoder.community.market.application.result;

public record MarketWalletActionRecoveryResult(int recoveredLeases, int reconciledCount, int skippedCount) {
}
