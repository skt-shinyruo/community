package com.nowcoder.community.wallet.dto;

import com.nowcoder.community.wallet.entity.TransferOrder;

public record CreateTransferResponse(long orderId,
                                     String requestId,
                                     long fromUserId,
                                     long toUserId,
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
