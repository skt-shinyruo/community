package com.nowcoder.community.market.infrastructure.persistence.dataobject;

import com.nowcoder.community.market.domain.model.MarketInventoryUnit;

public class MarketInventoryUnitDataObject extends MarketInventoryUnit {

    public static MarketInventoryUnitDataObject from(MarketInventoryUnit unit) {
        MarketInventoryUnitDataObject dataObject = new MarketInventoryUnitDataObject();
        dataObject.setInventoryUnitId(unit.getInventoryUnitId());
        dataObject.setListingId(unit.getListingId());
        dataObject.setSellerUserId(unit.getSellerUserId());
        dataObject.setPayloadType(unit.getPayloadType());
        dataObject.setPayloadContent(unit.getPayloadContent());
        dataObject.setStatus(unit.getStatus());
        dataObject.setReservedOrderId(unit.getReservedOrderId());
        dataObject.setDeliveredAt(unit.getDeliveredAt());
        dataObject.setCreateTime(unit.getCreateTime());
        return dataObject;
    }
}
