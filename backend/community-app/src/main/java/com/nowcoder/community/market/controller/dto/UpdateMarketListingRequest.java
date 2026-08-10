package com.nowcoder.community.market.controller.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UpdateMarketListingRequest(
        @NotBlank String title,
        @NotBlank String description,
        @NotNull @Min(1) Long unitPrice,
        @NotNull @Min(1) Integer minPurchaseQuantity,
        @NotNull @Min(1) Integer maxPurchaseQuantity
) {
}
