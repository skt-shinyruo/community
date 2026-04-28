package com.nowcoder.community.wallet.application.command;

import java.util.UUID;

public record WalletRewardCommand(String requestId, UUID userId, long amount, String sourceType) {
}
