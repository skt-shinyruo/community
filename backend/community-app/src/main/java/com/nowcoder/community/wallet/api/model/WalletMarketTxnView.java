package com.nowcoder.community.wallet.api.model;

public record WalletMarketTxnView(
        long txnId,
        String txnType,
        String status,
        long amount,
        String bizId
) {
}
