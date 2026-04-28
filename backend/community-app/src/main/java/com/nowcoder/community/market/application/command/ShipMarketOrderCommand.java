package com.nowcoder.community.market.application.command;

import java.util.UUID;

public record ShipMarketOrderCommand(
        UUID orderId,
        UUID sellerUserId,
        String carrierName,
        String trackingNo,
        String shippingRemark
) {
}
