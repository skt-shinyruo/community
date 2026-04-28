package com.nowcoder.community.wallet.application.result;

import java.util.UUID;

public record WalletSummaryResult(UUID userId, long balance, String status) {
}
