package com.nowcoder.community.market.domain.model;

import java.util.Date;
import java.util.UUID;

public class MarketShipment {

    private UUID shipmentId;
    private UUID orderId;
    private UUID sellerUserId;
    private String carrierName;
    private String trackingNo;
    private String shippingRemark;
    private Date shippedAt;
    private Date createTime;
    private Date updateTime;

    public UUID getShipmentId() {
        return shipmentId;
    }

    public void setShipmentId(UUID shipmentId) {
        this.shipmentId = shipmentId;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public void setOrderId(UUID orderId) {
        this.orderId = orderId;
    }

    public UUID getSellerUserId() {
        return sellerUserId;
    }

    public void setSellerUserId(UUID sellerUserId) {
        this.sellerUserId = sellerUserId;
    }

    public String getCarrierName() {
        return carrierName;
    }

    public void setCarrierName(String carrierName) {
        this.carrierName = carrierName;
    }

    public String getTrackingNo() {
        return trackingNo;
    }

    public void setTrackingNo(String trackingNo) {
        this.trackingNo = trackingNo;
    }

    public String getShippingRemark() {
        return shippingRemark;
    }

    public void setShippingRemark(String shippingRemark) {
        this.shippingRemark = shippingRemark;
    }

    public Date getShippedAt() {
        return shippedAt;
    }

    public void setShippedAt(Date shippedAt) {
        this.shippedAt = shippedAt;
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
