package com.nowcoder.community.market.application.command;

import java.util.UUID;

public record DeliverMarketOrderCommand(
        UUID orderId,
        UUID sellerUserId,
        String deliveryContent
) {
}
