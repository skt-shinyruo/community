package com.nowcoder.community.market.controller.dto;

import com.nowcoder.community.market.application.result.MarketOrderResult;

import java.util.Date;
import java.util.UUID;

public record MarketOrderResponse(
        UUID orderId,
        String requestId,
        UUID listingId,
        String goodsType,
        UUID sellerUserId,
        UUID buyerUserId,
        int quantity,
        long unitPriceSnapshot,
        long totalAmount,
        String deliveryModeSnapshot,
        String listingTitleSnapshot,
        String status,
        UUID escrowTxnId,
        UUID releaseTxnId,
        UUID refundTxnId,
        Date autoConfirmAt,
        Date createTime,
        Date updateTime
) {

    public static MarketOrderResponse from(MarketOrderResult order) {
        return new MarketOrderResponse(
                order.orderId(),
                order.requestId(),
                order.listingId(),
                order.goodsType(),
                order.sellerUserId(),
                order.buyerUserId(),
                order.quantity(),
                order.unitPriceSnapshot(),
                order.totalAmount(),
                order.deliveryModeSnapshot(),
                order.listingTitleSnapshot(),
                order.status(),
                order.escrowTxnId(),
                order.releaseTxnId(),
                order.refundTxnId(),
                order.autoConfirmAt(),
                order.createTime(),
                order.updateTime()
        );
    }
}
