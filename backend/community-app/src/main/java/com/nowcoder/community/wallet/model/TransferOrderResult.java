package com.nowcoder.community.wallet.model;

import com.nowcoder.community.wallet.entity.TransferOrder;

import java.util.UUID;

public record TransferOrderResult(UUID orderId,
                                  String requestId,
                                  UUID fromUserId,
                                  UUID toUserId,
                                  long amount,
                                  String status) {

    public static TransferOrderResult from(TransferOrder order) {
        return new TransferOrderResult(
                order.getOrderId(),
                order.getRequestId(),
                order.getFromUserId(),
                order.getToUserId(),
                order.getAmount(),
                order.getStatus()
        );
    }
}
