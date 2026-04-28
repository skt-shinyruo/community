package com.nowcoder.community.market.infrastructure.persistence.dataobject;

import com.nowcoder.community.market.domain.model.MarketAddress;

public class MarketAddressDataObject extends MarketAddress {

    public static MarketAddressDataObject from(MarketAddress address) {
        MarketAddressDataObject dataObject = new MarketAddressDataObject();
        dataObject.setAddressId(address.getAddressId());
        dataObject.setUserId(address.getUserId());
        dataObject.setReceiverName(address.getReceiverName());
        dataObject.setReceiverPhone(address.getReceiverPhone());
        dataObject.setProvince(address.getProvince());
        dataObject.setCity(address.getCity());
        dataObject.setDistrict(address.getDistrict());
        dataObject.setDetailAddress(address.getDetailAddress());
        dataObject.setPostalCode(address.getPostalCode());
        dataObject.setDefault(address.isDefault());
        dataObject.setStatus(address.getStatus());
        dataObject.setCreateTime(address.getCreateTime());
        dataObject.setUpdateTime(address.getUpdateTime());
        return dataObject;
    }
}
