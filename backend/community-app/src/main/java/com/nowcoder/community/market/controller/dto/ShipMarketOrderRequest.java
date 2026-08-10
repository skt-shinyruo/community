package com.nowcoder.community.market.controller.dto;

import jakarta.validation.constraints.NotBlank;

public record ShipMarketOrderRequest(
        @NotBlank String carrierName,
        @NotBlank String trackingNo,
        String shippingRemark
) {
}
