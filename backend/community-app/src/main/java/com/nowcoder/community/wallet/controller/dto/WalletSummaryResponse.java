package com.nowcoder.community.wallet.controller.dto;

import com.nowcoder.community.wallet.application.result.WalletSummaryResult;

import java.util.UUID;

public record WalletSummaryResponse(UUID userId, long balance, String status) {

    public static WalletSummaryResponse from(WalletSummaryResult result) {
        return new WalletSummaryResponse(result.userId(), result.balance(), result.status());
    }
}
