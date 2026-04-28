package com.nowcoder.community.market.infrastructure.persistence.dataobject;

import com.nowcoder.community.market.domain.model.MarketDelivery;

public class MarketDeliveryDataObject extends MarketDelivery {

    public static MarketDeliveryDataObject from(MarketDelivery delivery) {
        MarketDeliveryDataObject dataObject = new MarketDeliveryDataObject();
        dataObject.setDeliveryId(delivery.getDeliveryId());
        dataObject.setOrderId(delivery.getOrderId());
        dataObject.setSellerUserId(delivery.getSellerUserId());
        dataObject.setDeliveryType(delivery.getDeliveryType());
        dataObject.setDeliveryContent(delivery.getDeliveryContent());
        dataObject.setStatus(delivery.getStatus());
        dataObject.setDeliveredAt(delivery.getDeliveredAt());
        dataObject.setCreateTime(delivery.getCreateTime());
        return dataObject;
    }
}
