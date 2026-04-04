package com.nowcoder.community.market.dto;

import com.nowcoder.community.market.entity.MarketOrder;

import java.util.Date;

public record MarketOrderResponse(
        long orderId,
        String requestId,
        long listingId,
        String goodsType,
        int sellerUserId,
        int buyerUserId,
        int quantity,
        long unitPriceSnapshot,
        long totalAmount,
        String deliveryModeSnapshot,
        String listingTitleSnapshot,
        String status,
        Long escrowTxnId,
        Long releaseTxnId,
        Long refundTxnId,
        Date autoConfirmAt,
        Date createTime,
        Date updateTime
) {

    public static MarketOrderResponse from(MarketOrder order) {
        return new MarketOrderResponse(
                order.getOrderId(),
                order.getRequestId(),
                order.getListingId(),
                order.getGoodsType(),
                order.getSellerUserId(),
                order.getBuyerUserId(),
                order.getQuantity(),
                order.getUnitPriceSnapshot(),
                order.getTotalAmount(),
                order.getDeliveryModeSnapshot(),
                order.getListingTitleSnapshot(),
                order.getStatus(),
                order.getEscrowTxnId(),
                order.getReleaseTxnId(),
                order.getRefundTxnId(),
                order.getAutoConfirmAt(),
                order.getCreateTime(),
                order.getUpdateTime()
        );
    }
}
