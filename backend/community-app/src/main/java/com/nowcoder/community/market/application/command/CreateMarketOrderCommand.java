package com.nowcoder.community.market.application.command;

import java.util.UUID;

public record CreateMarketOrderCommand(
        UUID buyerUserId,
        String requestId,
        UUID listingId,
        int quantity,
        UUID addressId,
        String idempotencyKey
) {
}
