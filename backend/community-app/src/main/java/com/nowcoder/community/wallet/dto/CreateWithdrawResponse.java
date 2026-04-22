package com.nowcoder.community.wallet.dto;

import com.nowcoder.community.wallet.entity.WithdrawOrder;

import java.util.UUID;

public record CreateWithdrawResponse(UUID orderId, String requestId, UUID userId, long amount, String status) {

    public static CreateWithdrawResponse from(WithdrawOrder order) {
        return new CreateWithdrawResponse(
                order.getOrderId(),
                order.getRequestId(),
                order.getUserId(),
                order.getAmount(),
                order.getStatus()
        );
    }
}
