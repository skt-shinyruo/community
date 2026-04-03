package com.nowcoder.community.market.dto;

import com.nowcoder.community.market.entity.VirtualInventoryUnit;

import java.util.Date;

public record VirtualInventoryUnitResponse(
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

    public static VirtualInventoryUnitResponse from(VirtualInventoryUnit unit) {
        return new VirtualInventoryUnitResponse(
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
