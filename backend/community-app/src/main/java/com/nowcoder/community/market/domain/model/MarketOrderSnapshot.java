package com.nowcoder.community.market.domain.model;

import java.util.Date;
import java.util.UUID;

public record MarketOrderSnapshot(
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
        UUID addressIdSnapshot,
        String receiverNameSnapshot,
        String receiverPhoneSnapshot,
        String provinceSnapshot,
        String citySnapshot,
        String districtSnapshot,
        String detailAddressSnapshot,
        String postalCodeSnapshot,
        Date createTime,
        Date updateTime
) {
    public MarketOrderSnapshot {
        autoConfirmAt = copy(autoConfirmAt);
        createTime = copy(createTime);
        updateTime = copy(updateTime);
    }

    public Date autoConfirmAt() {
        return copy(autoConfirmAt);
    }

    public Date createTime() {
        return copy(createTime);
    }

    public Date updateTime() {
        return copy(updateTime);
    }

    private static Date copy(Date value) {
        return value == null ? null : new Date(value.getTime());
    }
}
