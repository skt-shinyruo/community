package com.nowcoder.community.market.controller.dto;

import com.nowcoder.community.market.application.result.MarketListingDetailResult;

import java.util.Date;
import java.util.UUID;

public record MarketListingDetailResponse(
        UUID listingId,
        UUID sellerUserId,
        String goodsType,
        String title,
        String description,
        long unitPrice,
        String deliveryMode,
        String stockMode,
        int stockTotal,
        int stockAvailable,
        int minPurchaseQuantity,
        int maxPurchaseQuantity,
        String status,
        Date createTime,
        Date updateTime
) {

    public static MarketListingDetailResponse from(MarketListingDetailResult listing) {
        return new MarketListingDetailResponse(
                listing.listingId(),
                listing.sellerUserId(),
                listing.goodsType(),
                listing.title(),
                listing.description(),
                listing.unitPrice(),
                listing.deliveryMode(),
                listing.stockMode(),
                listing.stockTotal(),
                listing.stockAvailable(),
                listing.minPurchaseQuantity(),
                listing.maxPurchaseQuantity(),
                listing.status(),
                listing.createTime(),
                listing.updateTime()
        );
    }
}
