package com.nowcoder.community.market.controller.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class UpdateMarketListingRequest {

    @NotBlank
    private String title;

    @NotBlank
    private String description;

    @NotNull
    @Min(1)
    private Long unitPrice;

    @NotNull
    @Min(1)
    private Integer minPurchaseQuantity;

    @NotNull
    @Min(1)
    private Integer maxPurchaseQuantity;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Long getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(Long unitPrice) {
        this.unitPrice = unitPrice;
    }

    public Integer getMinPurchaseQuantity() {
        return minPurchaseQuantity;
    }

    public void setMinPurchaseQuantity(Integer minPurchaseQuantity) {
        this.minPurchaseQuantity = minPurchaseQuantity;
    }

    public Integer getMaxPurchaseQuantity() {
        return maxPurchaseQuantity;
    }

    public void setMaxPurchaseQuantity(Integer maxPurchaseQuantity) {
        this.maxPurchaseQuantity = maxPurchaseQuantity;
    }
}
