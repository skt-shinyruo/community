package com.nowcoder.community.market.controller.dto;

import com.nowcoder.community.market.application.result.MarketInventoryUnitResult;

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

    public static MarketInventoryUnitResponse from(MarketInventoryUnitResult unit) {
        return new MarketInventoryUnitResponse(
                unit.inventoryUnitId(),
                unit.listingId(),
                unit.sellerUserId(),
                unit.payloadType(),
                unit.payloadContent(),
                unit.status(),
                unit.reservedOrderId(),
                unit.deliveredAt(),
                unit.createTime()
        );
    }
}
