package com.nowcoder.community.market.dto;

import com.nowcoder.community.market.entity.MarketListing;

public record MarketListingResponse(
        long listingId,
        int sellerUserId,
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

    public static MarketListingResponse from(MarketListing listing) {
        return new MarketListingResponse(
                listing.getListingId(),
                listing.getSellerUserId(),
                listing.getGoodsType(),
                listing.getTitle(),
                listing.getDescription(),
                listing.getUnitPrice(),
                listing.getDeliveryMode(),
                listing.getStockMode(),
                listing.getStockTotal(),
                listing.getStockAvailable(),
                listing.getMinPurchaseQuantity(),
                listing.getMaxPurchaseQuantity(),
                listing.getStatus()
        );
    }
}
