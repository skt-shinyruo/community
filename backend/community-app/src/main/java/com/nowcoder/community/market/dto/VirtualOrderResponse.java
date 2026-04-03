package com.nowcoder.community.market.dto;

import com.nowcoder.community.market.entity.VirtualOrder;

import java.util.Date;

public record VirtualOrderResponse(
        long orderId,
        String requestId,
        long listingId,
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

    public static VirtualOrderResponse from(VirtualOrder order) {
        return new VirtualOrderResponse(
                order.getOrderId(),
                order.getRequestId(),
                order.getListingId(),
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
