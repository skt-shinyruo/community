package com.nowcoder.community.market.application.result;

import com.nowcoder.community.market.domain.model.MarketOrder;

import java.util.Date;
import java.util.UUID;

public record MarketOrderResult(
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

    public static MarketOrderResult from(MarketOrder order) {
        return new MarketOrderResult(
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
