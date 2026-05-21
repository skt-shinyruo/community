package com.nowcoder.community.market.domain.model;

import java.util.Objects;
import java.util.UUID;

public record MarketOrderPlacement(
        UUID orderId,
        String requestId,
        UUID listingId,
        MarketGoodsType goodsType,
        UUID sellerUserId,
        UUID buyerUserId,
        int quantity,
        long unitPriceSnapshot,
        long totalAmount,
        MarketDeliveryMode deliveryModeSnapshot,
        String listingTitleSnapshot,
        MarketAddressSnapshot addressSnapshot
) {
    public MarketOrderPlacement {
        Objects.requireNonNull(orderId, "orderId must not be null");
        if (requestId == null || requestId.isBlank()) {
            throw new IllegalArgumentException("requestId must not be blank");
        }
        Objects.requireNonNull(listingId, "listingId must not be null");
        Objects.requireNonNull(goodsType, "goodsType must not be null");
        Objects.requireNonNull(sellerUserId, "sellerUserId must not be null");
        Objects.requireNonNull(buyerUserId, "buyerUserId must not be null");
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
        if (unitPriceSnapshot < 0) {
            throw new IllegalArgumentException("unitPriceSnapshot must not be negative");
        }
        if (totalAmount < 0) {
            throw new IllegalArgumentException("totalAmount must not be negative");
        }
        Objects.requireNonNull(deliveryModeSnapshot, "deliveryModeSnapshot must not be null");
        if (goodsType.isPhysical() && addressSnapshot == null) {
            throw new IllegalArgumentException("addressSnapshot must not be null for physical goods");
        }
        if (goodsType.isVirtual() && addressSnapshot != null) {
            throw new IllegalArgumentException("addressSnapshot must be null for virtual goods");
        }
    }
}
