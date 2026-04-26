package com.nowcoder.community.wallet.model;

import com.nowcoder.community.wallet.entity.WithdrawOrder;

import java.util.UUID;

public record WithdrawOrderResult(UUID orderId, String requestId, UUID userId, long amount, String status) {

    public static WithdrawOrderResult from(WithdrawOrder order) {
        return new WithdrawOrderResult(
                order.getOrderId(),
                order.getRequestId(),
                order.getUserId(),
                order.getAmount(),
                order.getStatus()
        );
    }
}
