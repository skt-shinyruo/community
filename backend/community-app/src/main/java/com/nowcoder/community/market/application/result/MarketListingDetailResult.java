package com.nowcoder.community.market.application.result;

import com.nowcoder.community.market.domain.model.MarketListing;

import java.util.Date;
import java.util.UUID;

public record MarketListingDetailResult(
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

    public static MarketListingDetailResult from(MarketListing listing) {
        return new MarketListingDetailResult(
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
