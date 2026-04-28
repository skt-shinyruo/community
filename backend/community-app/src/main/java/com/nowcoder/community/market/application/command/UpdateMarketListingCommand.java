package com.nowcoder.community.market.application.command;

import java.util.UUID;

public record UpdateMarketListingCommand(
        UUID sellerUserId,
        UUID listingId,
        String title,
        String description,
        Long unitPrice,
        Integer minPurchaseQuantity,
        Integer maxPurchaseQuantity
) {
}
