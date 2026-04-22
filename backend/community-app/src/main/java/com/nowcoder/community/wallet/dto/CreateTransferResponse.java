package com.nowcoder.community.wallet.dto;

import com.nowcoder.community.wallet.entity.TransferOrder;

import java.util.UUID;

public record CreateTransferResponse(UUID orderId,
                                     String requestId,
                                     UUID fromUserId,
                                     UUID toUserId,
                                     long amount,
                                     String status) {

    public static CreateTransferResponse from(TransferOrder order) {
        return new CreateTransferResponse(
                order.getOrderId(),
                order.getRequestId(),
                order.getFromUserId(),
                order.getToUserId(),
                order.getAmount(),
                order.getStatus()
        );
    }
}
