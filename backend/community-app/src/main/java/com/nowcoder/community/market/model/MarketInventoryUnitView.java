package com.nowcoder.community.market.model;

import com.nowcoder.community.market.entity.MarketInventoryUnit;

import java.util.Date;
import java.util.UUID;

public record MarketInventoryUnitView(
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

    public static MarketInventoryUnitView from(MarketInventoryUnit unit) {
        return new MarketInventoryUnitView(
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
