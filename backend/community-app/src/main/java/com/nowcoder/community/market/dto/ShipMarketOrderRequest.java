package com.nowcoder.community.market.dto;

import jakarta.validation.constraints.NotBlank;

public class ShipMarketOrderRequest {

    @NotBlank
    private String carrierName;

    @NotBlank
    private String trackingNo;

    private String shippingRemark;

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
}
