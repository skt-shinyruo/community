package com.nowcoder.community.market.application.command;

import java.util.UUID;

public record CreateMarketListingCommand(
        UUID sellerUserId,
        String goodsType,
        String title,
        String description,
        Long unitPrice,
        String deliveryMode,
        String stockMode,
        Integer stockTotal,
        Integer minPurchaseQuantity,
        Integer maxPurchaseQuantity,
        AddMarketInventoryBatchCommand inventory
) {
}
