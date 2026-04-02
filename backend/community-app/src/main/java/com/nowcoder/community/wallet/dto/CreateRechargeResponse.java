package com.nowcoder.community.wallet.dto;

import com.nowcoder.community.wallet.entity.RechargeOrder;

public record CreateRechargeResponse(long orderId, String requestId, long userId, long amount, String status) {

    public static CreateRechargeResponse from(RechargeOrder order) {
        return new CreateRechargeResponse(
                order.getOrderId(),
                order.getRequestId(),
                order.getUserId(),
                order.getAmount(),
                order.getStatus()
        );
    }
}
