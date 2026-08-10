package com.nowcoder.community.market.controller.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateMarketAddressRequest(
        @NotBlank String receiverName,
        @NotBlank String receiverPhone,
        @NotBlank String province,
        @NotBlank String city,
        @NotBlank String district,
        @NotBlank String detailAddress,
        String postalCode,
        boolean defaultAddress
) {
}
