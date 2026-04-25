package com.nowcoder.community.market.service;

public record MarketWalletActionRecoveryResult(int recoveredLeases, int reconciledCount, int skippedCount) {
}
