package com.nowcoder.community.market.infrastructure.persistence.dataobject;

import com.nowcoder.community.market.domain.model.MarketOrder;

public class MarketOrderDataObject extends MarketOrder {

    public static MarketOrderDataObject from(MarketOrder order) {
        MarketOrderDataObject dataObject = new MarketOrderDataObject();
        dataObject.setOrderId(order.getOrderId());
        dataObject.setRequestId(order.getRequestId());
        dataObject.setListingId(order.getListingId());
        dataObject.setGoodsType(order.getGoodsType());
        dataObject.setSellerUserId(order.getSellerUserId());
        dataObject.setBuyerUserId(order.getBuyerUserId());
        dataObject.setQuantity(order.getQuantity());
        dataObject.setUnitPriceSnapshot(order.getUnitPriceSnapshot());
        dataObject.setTotalAmount(order.getTotalAmount());
        dataObject.setDeliveryModeSnapshot(order.getDeliveryModeSnapshot());
        dataObject.setListingTitleSnapshot(order.getListingTitleSnapshot());
        dataObject.setStatus(order.getStatus());
        dataObject.setEscrowTxnId(order.getEscrowTxnId());
        dataObject.setReleaseTxnId(order.getReleaseTxnId());
        dataObject.setRefundTxnId(order.getRefundTxnId());
        dataObject.setAutoConfirmAt(order.getAutoConfirmAt());
        dataObject.setAddressIdSnapshot(order.getAddressIdSnapshot());
        dataObject.setReceiverNameSnapshot(order.getReceiverNameSnapshot());
        dataObject.setReceiverPhoneSnapshot(order.getReceiverPhoneSnapshot());
        dataObject.setProvinceSnapshot(order.getProvinceSnapshot());
        dataObject.setCitySnapshot(order.getCitySnapshot());
        dataObject.setDistrictSnapshot(order.getDistrictSnapshot());
        dataObject.setDetailAddressSnapshot(order.getDetailAddressSnapshot());
        dataObject.setPostalCodeSnapshot(order.getPostalCodeSnapshot());
        dataObject.setCreateTime(order.getCreateTime());
        dataObject.setUpdateTime(order.getUpdateTime());
        return dataObject;
    }
}
