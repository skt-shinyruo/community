package com.nowcoder.community.wallet.controller.dto;

import com.nowcoder.community.wallet.application.result.TransferOrderResult;

import java.util.UUID;

public record CreateTransferResponse(UUID orderId,
                                     String requestId,
                                     UUID fromUserId,
                                     UUID toUserId,
                                     long amount,
                                     String status) {

    public static CreateTransferResponse from(TransferOrderResult result) {
        return new CreateTransferResponse(
                result.orderId(),
                result.requestId(),
                result.fromUserId(),
                result.toUserId(),
                result.amount(),
                result.status()
        );
    }
}
