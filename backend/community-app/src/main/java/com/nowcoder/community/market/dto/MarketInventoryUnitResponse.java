package com.nowcoder.community.market.dto;

import com.nowcoder.community.market.entity.MarketInventoryUnit;

import java.util.Date;

public record MarketInventoryUnitResponse(
        long inventoryUnitId,
        long listingId,
        int sellerUserId,
        String payloadType,
        String payloadContent,
        String status,
        Long reservedOrderId,
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
