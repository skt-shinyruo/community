package com.nowcoder.community.wallet.dto;

import java.util.UUID;

public record WalletSummaryResponse(UUID userId, long balance, String status) {
}
