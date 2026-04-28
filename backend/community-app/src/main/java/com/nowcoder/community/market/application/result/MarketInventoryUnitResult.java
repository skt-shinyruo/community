package com.nowcoder.community.market.application.result;

import com.nowcoder.community.market.domain.model.MarketInventoryUnit;

import java.util.Date;
import java.util.UUID;

public record MarketInventoryUnitResult(
        UUID inventoryUnitId,
        UUID listingId,
        UUID sellerUserId,
        String payloadType,
        String payloadContent,
        String status,
        UUID reservedOrderId,
        Date deliveredAt,
        Date createTime
) {

    public static MarketInventoryUnitResult from(MarketInventoryUnit unit) {
        return new MarketInventoryUnitResult(
                unit.getInventoryUnitId(),
                unit.getListingId(),
                unit.getSellerUserId(),
                unit.getPayloadType(),
                unit.getPayloadContent(),
                unit.getStatus(),
                unit.getReservedOrderId(),
                unit.getDeliveredAt(),
                unit.getCreateTime()
        );
    }
}
