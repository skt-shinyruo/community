package com.nowcoder.community.market.dto;

import com.nowcoder.community.market.entity.MarketInventoryUnit;

import java.util.Date;
import java.util.UUID;

public record MarketInventoryUnitResponse(
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

    public static MarketInventoryUnitResponse from(MarketInventoryUnit unit) {
        return new MarketInventoryUnitResponse(
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
