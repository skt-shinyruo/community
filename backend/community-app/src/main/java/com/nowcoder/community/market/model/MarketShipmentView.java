package com.nowcoder.community.market.model;

import com.nowcoder.community.market.entity.MarketShipment;

import java.util.Date;
import java.util.UUID;

public record MarketShipmentView(
        UUID shipmentId,
        UUID orderId,
        UUID sellerUserId,
        String carrierName,
        String trackingNo,
        String shippingRemark,
        Date shippedAt,
        Date createTime,
        Date updateTime
) {

    public static MarketShipmentView from(MarketShipment shipment) {
        if (shipment == null) {
            return null;
        }
        return new MarketShipmentView(
                shipment.getShipmentId(),
                shipment.getOrderId(),
                shipment.getSellerUserId(),
                shipment.getCarrierName(),
                shipment.getTrackingNo(),
                shipment.getShippingRemark(),
                shipment.getShippedAt(),
                shipment.getCreateTime(),
                shipment.getUpdateTime()
        );
    }
}
