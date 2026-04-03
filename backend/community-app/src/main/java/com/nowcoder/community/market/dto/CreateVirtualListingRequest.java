package com.nowcoder.community.market.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class CreateVirtualListingRequest {

    @NotBlank
    private String title;

    @NotBlank
    private String description;

    @NotNull
    @Min(1)
    private Long unitPrice;

    @NotBlank
    private String deliveryMode;

    @NotBlank
    private String stockMode;

    @NotNull
    @Min(0)
    private Integer stockTotal;

    @NotNull
    @Min(1)
    private Integer minPurchaseQuantity;

    @NotNull
    @Min(1)
    private Integer maxPurchaseQuantity;

    @Valid
    private AddVirtualInventoryBatchRequest inventory;

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

    public String getDeliveryMode() {
        return deliveryMode;
    }

    public void setDeliveryMode(String deliveryMode) {
        this.deliveryMode = deliveryMode;
    }

    public String getStockMode() {
        return stockMode;
    }

    public void setStockMode(String stockMode) {
        this.stockMode = stockMode;
    }

    public Integer getStockTotal() {
        return stockTotal;
    }

    public void setStockTotal(Integer stockTotal) {
        this.stockTotal = stockTotal;
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

    public AddVirtualInventoryBatchRequest getInventory() {
        return inventory;
    }

    public void setInventory(AddVirtualInventoryBatchRequest inventory) {
        this.inventory = inventory;
    }
}
