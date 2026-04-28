package com.nowcoder.community.wallet.controller.dto;

import com.nowcoder.community.wallet.application.result.RechargeOrderResult;

import java.util.UUID;

public record CreateRechargeResponse(UUID orderId, String requestId, UUID userId, long amount, String status) {

    public static CreateRechargeResponse from(RechargeOrderResult result) {
        return new CreateRechargeResponse(
                result.orderId(),
                result.requestId(),
                result.userId(),
                result.amount(),
                result.status()
        );
    }
}
