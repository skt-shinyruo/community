package com.nowcoder.community.market.entity;

import java.util.Date;
import java.util.UUID;

public class MarketOrder {

    private UUID orderId;
    private String requestId;
    private UUID listingId;
    private String goodsType;
    private UUID sellerUserId;
    private UUID buyerUserId;
    private int quantity;
    private long unitPriceSnapshot;
    private long totalAmount;
    private String deliveryModeSnapshot;
    private String listingTitleSnapshot;
    private String status;
    private UUID escrowTxnId;
    private UUID releaseTxnId;
    private UUID refundTxnId;
    private Date autoConfirmAt;
    private String receiverNameSnapshot;
    private String receiverPhoneSnapshot;
    private String provinceSnapshot;
    private String citySnapshot;
    private String districtSnapshot;
    private String detailAddressSnapshot;
    private String postalCodeSnapshot;
    private Date createTime;
    private Date updateTime;

    public UUID getOrderId() {
        return orderId;
    }

    public void setOrderId(UUID orderId) {
        this.orderId = orderId;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public UUID getListingId() {
        return listingId;
    }

    public void setListingId(UUID listingId) {
        this.listingId = listingId;
    }

    public String getGoodsType() {
        return goodsType;
    }

    public void setGoodsType(String goodsType) {
        this.goodsType = goodsType;
    }

    public UUID getSellerUserId() {
        return sellerUserId;
    }

    public void setSellerUserId(UUID sellerUserId) {
        this.sellerUserId = sellerUserId;
    }

    public UUID getBuyerUserId() {
        return buyerUserId;
    }

    public void setBuyerUserId(UUID buyerUserId) {
        this.buyerUserId = buyerUserId;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public long getUnitPriceSnapshot() {
        return unitPriceSnapshot;
    }

    public void setUnitPriceSnapshot(long unitPriceSnapshot) {
        this.unitPriceSnapshot = unitPriceSnapshot;
    }

    public long getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(long totalAmount) {
        this.totalAmount = totalAmount;
    }

    public String getDeliveryModeSnapshot() {
        return deliveryModeSnapshot;
    }

    public void setDeliveryModeSnapshot(String deliveryModeSnapshot) {
        this.deliveryModeSnapshot = deliveryModeSnapshot;
    }

    public String getListingTitleSnapshot() {
        return listingTitleSnapshot;
    }

    public void setListingTitleSnapshot(String listingTitleSnapshot) {
        this.listingTitleSnapshot = listingTitleSnapshot;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public UUID getEscrowTxnId() {
        return escrowTxnId;
    }

    public void setEscrowTxnId(UUID escrowTxnId) {
        this.escrowTxnId = escrowTxnId;
    }

    public UUID getReleaseTxnId() {
        return releaseTxnId;
    }

    public void setReleaseTxnId(UUID releaseTxnId) {
        this.releaseTxnId = releaseTxnId;
    }

    public UUID getRefundTxnId() {
        return refundTxnId;
    }

    public void setRefundTxnId(UUID refundTxnId) {
        this.refundTxnId = refundTxnId;
    }

    public Date getAutoConfirmAt() {
        return autoConfirmAt;
    }

    public void setAutoConfirmAt(Date autoConfirmAt) {
        this.autoConfirmAt = autoConfirmAt;
    }

    public String getReceiverNameSnapshot() {
        return receiverNameSnapshot;
    }

    public void setReceiverNameSnapshot(String receiverNameSnapshot) {
        this.receiverNameSnapshot = receiverNameSnapshot;
    }

    public String getReceiverPhoneSnapshot() {
        return receiverPhoneSnapshot;
    }

    public void setReceiverPhoneSnapshot(String receiverPhoneSnapshot) {
        this.receiverPhoneSnapshot = receiverPhoneSnapshot;
    }

    public String getProvinceSnapshot() {
        return provinceSnapshot;
    }

    public void setProvinceSnapshot(String provinceSnapshot) {
        this.provinceSnapshot = provinceSnapshot;
    }

    public String getCitySnapshot() {
        return citySnapshot;
    }

    public void setCitySnapshot(String citySnapshot) {
        this.citySnapshot = citySnapshot;
    }

    public String getDistrictSnapshot() {
        return districtSnapshot;
    }

    public void setDistrictSnapshot(String districtSnapshot) {
        this.districtSnapshot = districtSnapshot;
    }

    public String getDetailAddressSnapshot() {
        return detailAddressSnapshot;
    }

    public void setDetailAddressSnapshot(String detailAddressSnapshot) {
        this.detailAddressSnapshot = detailAddressSnapshot;
    }

    public String getPostalCodeSnapshot() {
        return postalCodeSnapshot;
    }

    public void setPostalCodeSnapshot(String postalCodeSnapshot) {
        this.postalCodeSnapshot = postalCodeSnapshot;
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
