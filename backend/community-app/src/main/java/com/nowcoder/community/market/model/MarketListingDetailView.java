package com.nowcoder.community.market.model;

import com.nowcoder.community.market.entity.MarketListing;

import java.util.Date;
import java.util.UUID;

public record MarketListingDetailView(
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

    public static MarketListingDetailView from(MarketListing listing) {
        return new MarketListingDetailView(
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
                listing.getStatus(),
                listing.getCreateTime(),
                listing.getUpdateTime()
        );
    }
}
