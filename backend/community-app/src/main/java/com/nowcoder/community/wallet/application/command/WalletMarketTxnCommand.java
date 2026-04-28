package com.nowcoder.community.wallet.application.command;

import java.util.UUID;

public record WalletMarketTxnCommand(String requestId, UUID userId, long amount, String bizId) {
}
