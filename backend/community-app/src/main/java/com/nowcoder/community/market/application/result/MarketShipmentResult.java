package com.nowcoder.community.market.application.result;

import com.nowcoder.community.market.domain.model.MarketShipment;

import java.util.Date;
import java.util.UUID;

public record MarketShipmentResult(
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

    public static MarketShipmentResult from(MarketShipment shipment) {
        if (shipment == null) {
            return null;
        }
        return new MarketShipmentResult(
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
