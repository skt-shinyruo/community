package com.nowcoder.community.market.infrastructure.persistence.dataobject;

import com.nowcoder.community.market.domain.model.MarketListing;

public class MarketListingDataObject extends MarketListing {

    public static MarketListingDataObject from(MarketListing listing) {
        MarketListingDataObject dataObject = new MarketListingDataObject();
        dataObject.setListingId(listing.getListingId());
        dataObject.setSellerUserId(listing.getSellerUserId());
        dataObject.setGoodsType(listing.getGoodsType());
        dataObject.setTitle(listing.getTitle());
        dataObject.setDescription(listing.getDescription());
        dataObject.setUnitPrice(listing.getUnitPrice());
        dataObject.setDeliveryMode(listing.getDeliveryMode());
        dataObject.setStockMode(listing.getStockMode());
        dataObject.setStockTotal(listing.getStockTotal());
        dataObject.setStockAvailable(listing.getStockAvailable());
        dataObject.setMinPurchaseQuantity(listing.getMinPurchaseQuantity());
        dataObject.setMaxPurchaseQuantity(listing.getMaxPurchaseQuantity());
        dataObject.setStatus(listing.getStatus());
        dataObject.setCreateTime(listing.getCreateTime());
        dataObject.setUpdateTime(listing.getUpdateTime());
        return dataObject;
    }
}
