package com.nowcoder.community.wallet.application.result;

import java.util.UUID;

public record WalletMarketTxnResult(UUID txnId, String txnType, String status, long amount, String bizId) {
}
