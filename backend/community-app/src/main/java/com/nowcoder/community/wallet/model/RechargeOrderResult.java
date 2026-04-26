package com.nowcoder.community.wallet.model;

import com.nowcoder.community.wallet.entity.RechargeOrder;

import java.util.UUID;

public record RechargeOrderResult(UUID orderId, String requestId, UUID userId, long amount, String status) {

    public static RechargeOrderResult from(RechargeOrder order) {
        return new RechargeOrderResult(
                order.getOrderId(),
                order.getRequestId(),
                order.getUserId(),
                order.getAmount(),
                order.getStatus()
        );
    }
}
