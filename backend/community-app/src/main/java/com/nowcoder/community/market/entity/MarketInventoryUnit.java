package com.nowcoder.community.market.entity;

import java.util.Date;
import java.util.UUID;

public class MarketInventoryUnit {

    private UUID inventoryUnitId;
    private UUID listingId;
    private UUID sellerUserId;
    private String payloadType;
    private String payloadContent;
    private String status;
    private UUID reservedOrderId;
    private Date deliveredAt;
    private Date createTime;

    public UUID getInventoryUnitId() {
        return inventoryUnitId;
    }

    public void setInventoryUnitId(UUID inventoryUnitId) {
        this.inventoryUnitId = inventoryUnitId;
    }

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

    public String getPayloadType() {
        return payloadType;
    }

    public void setPayloadType(String payloadType) {
        this.payloadType = payloadType;
    }

    public String getPayloadContent() {
        return payloadContent;
    }

    public void setPayloadContent(String payloadContent) {
        this.payloadContent = payloadContent;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public UUID getReservedOrderId() {
        return reservedOrderId;
    }

    public void setReservedOrderId(UUID reservedOrderId) {
        this.reservedOrderId = reservedOrderId;
    }

    public Date getDeliveredAt() {
        return deliveredAt;
    }

    public void setDeliveredAt(Date deliveredAt) {
        this.deliveredAt = deliveredAt;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }
}
