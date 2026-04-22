package com.nowcoder.community.wallet.api.model;

import java.util.UUID;

public record WalletMarketTxnView(
        UUID txnId,
        String txnType,
        String status,
        long amount,
        String bizId
) {
}
