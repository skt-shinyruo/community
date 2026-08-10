package com.nowcoder.community.market.controller.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateMarketListingRequest(
        @NotBlank String goodsType,
        @NotBlank String title,
        @NotBlank String description,
        @NotNull @Min(1) Long unitPrice,
        String deliveryMode,
        String stockMode,
        @NotNull @Min(0) Integer stockTotal,
        @NotNull @Min(1) Integer minPurchaseQuantity,
        @NotNull @Min(1) Integer maxPurchaseQuantity,
        @Valid AddMarketInventoryBatchRequest inventory
) {
}
