package com.nowcoder.community.market.dto;

import com.nowcoder.community.market.entity.VirtualDelivery;
import com.nowcoder.community.market.entity.VirtualOrder;

import java.util.Date;
import java.util.List;

public record VirtualOrderDetailResponse(
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
        Date updateTime,
        List<String> deliveryContents
) {

    public static VirtualOrderDetailResponse from(VirtualOrder order, List<VirtualDelivery> deliveries) {
        List<String> deliveryContents = deliveries == null ? List.of() : deliveries.stream()
                .map(VirtualDelivery::getDeliveryContent)
                .toList();
        return new VirtualOrderDetailResponse(
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
                order.getUpdateTime(),
                deliveryContents
        );
    }
}
