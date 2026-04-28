package com.nowcoder.community.market.infrastructure.persistence.dataobject;

import com.nowcoder.community.market.domain.model.MarketShipment;

public class MarketShipmentDataObject extends MarketShipment {

    public static MarketShipmentDataObject from(MarketShipment shipment) {
        MarketShipmentDataObject dataObject = new MarketShipmentDataObject();
        dataObject.setShipmentId(shipment.getShipmentId());
        dataObject.setOrderId(shipment.getOrderId());
        dataObject.setSellerUserId(shipment.getSellerUserId());
        dataObject.setCarrierName(shipment.getCarrierName());
        dataObject.setTrackingNo(shipment.getTrackingNo());
        dataObject.setShippingRemark(shipment.getShippingRemark());
        dataObject.setShippedAt(shipment.getShippedAt());
        dataObject.setCreateTime(shipment.getCreateTime());
        dataObject.setUpdateTime(shipment.getUpdateTime());
        return dataObject;
    }
}
