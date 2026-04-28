package com.nowcoder.community.market.domain.model;

import java.util.Date;
import java.util.UUID;

public class MarketListing {

    private UUID listingId;
    private UUID sellerUserId;
    private String goodsType;
    private String title;
    private String description;
    private long unitPrice;
    private String deliveryMode;
    private String stockMode;
    private int stockTotal;
    private int stockAvailable;
    private int minPurchaseQuantity;
    private int maxPurchaseQuantity;
    private String status;
    private Date createTime;
    private Date updateTime;

    public UUID getListingId() {
        return listingId;
    }

    public void setListingId(UUID listingId) {
        this.listingId = listingId;
    }

    public UUID getSellerUserId() {
        return sellerUserId;
    }

    public void setSellerUserId(UUID sellerUserId) {
        this.sellerUserId = sellerUserId;
    }

    public String getGoodsType() {
        return goodsType;
    }

    public void setGoodsType(String goodsType) {
        this.goodsType = goodsType;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public long getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(long unitPrice) {
        this.unitPrice = unitPrice;
    }

    public String getDeliveryMode() {
        return deliveryMode;
    }

    public void setDeliveryMode(String deliveryMode) {
        this.deliveryMode = deliveryMode;
    }

    public String getStockMode() {
        return stockMode;
    }

    public void setStockMode(String stockMode) {
        this.stockMode = stockMode;
    }

    public int getStockTotal() {
        return stockTotal;
    }

    public void setStockTotal(int stockTotal) {
        this.stockTotal = stockTotal;
    }

    public int getStockAvailable() {
        return stockAvailable;
    }

    public void setStockAvailable(int stockAvailable) {
        this.stockAvailable = stockAvailable;
    }

    public int getMinPurchaseQuantity() {
        return minPurchaseQuantity;
    }

    public void setMinPurchaseQuantity(int minPurchaseQuantity) {
        this.minPurchaseQuantity = minPurchaseQuantity;
    }

    public int getMaxPurchaseQuantity() {
        return maxPurchaseQuantity;
    }

    public void setMaxPurchaseQuantity(int maxPurchaseQuantity) {
        this.maxPurchaseQuantity = maxPurchaseQuantity;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    public Date getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime) {
        this.updateTime = updateTime;
    }
}
