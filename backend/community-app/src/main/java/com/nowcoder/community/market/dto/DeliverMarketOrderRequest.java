package com.nowcoder.community.market.dto;

import jakarta.validation.constraints.NotBlank;

public class DeliverMarketOrderRequest {

    @NotBlank
    private String deliveryContent;

    public String getDeliveryContent() {
        return deliveryContent;
    }

    public void setDeliveryContent(String deliveryContent) {
        this.deliveryContent = deliveryContent;
    }
}
