package com.nowcoder.community.wallet.controller.dto;

import com.nowcoder.community.wallet.application.result.WithdrawOrderResult;

import java.util.UUID;

public record CreateWithdrawResponse(UUID orderId, String requestId, UUID userId, long amount, String status) {

    public static CreateWithdrawResponse from(WithdrawOrderResult result) {
        return new CreateWithdrawResponse(
                result.orderId(),
                result.requestId(),
                result.userId(),
                result.amount(),
                result.status()
        );
    }
}
