package com.nowcoder.community.market.dto;

import com.nowcoder.community.market.model.MarketListingResult;

import java.util.UUID;

public record MarketListingResponse(
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
        String status
) {

    public static MarketListingResponse from(MarketListingResult listing) {
        return new MarketListingResponse(
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
                listing.status()
        );
    }
}
