package com.nowcoder.community.market.entity;

import java.util.Date;

public class MarketInventoryUnit {

    private long inventoryUnitId;
    private long listingId;
    private int sellerUserId;
    private String payloadType;
    private String payloadContent;
    private String status;
    private Long reservedOrderId;
    private Date deliveredAt;
    private Date createTime;

    public long getInventoryUnitId() {
        return inventoryUnitId;
    }

    public void setInventoryUnitId(long inventoryUnitId) {
        this.inventoryUnitId = inventoryUnitId;
    }

    public long getListingId() {
        return listingId;
    }

    public void setListingId(long listingId) {
        this.listingId = listingId;
    }

    public int getSellerUserId() {
        return sellerUserId;
    }

    public void setSellerUserId(int sellerUserId) {
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

    public Long getReservedOrderId() {
        return reservedOrderId;
    }

    public void setReservedOrderId(Long reservedOrderId) {
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
