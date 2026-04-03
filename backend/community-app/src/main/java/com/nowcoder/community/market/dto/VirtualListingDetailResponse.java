package com.nowcoder.community.market.dto;

import com.nowcoder.community.market.entity.VirtualListing;

import java.util.Date;

public record VirtualListingDetailResponse(
        long listingId,
        int sellerUserId,
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

    public static VirtualListingDetailResponse from(VirtualListing listing) {
        return new VirtualListingDetailResponse(
                listing.getListingId(),
                listing.getSellerUserId(),
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
