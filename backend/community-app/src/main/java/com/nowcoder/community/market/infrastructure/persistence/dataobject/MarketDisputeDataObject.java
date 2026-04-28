package com.nowcoder.community.market.infrastructure.persistence.dataobject;

import com.nowcoder.community.market.domain.model.MarketDispute;

public class MarketDisputeDataObject extends MarketDispute {

    public static MarketDisputeDataObject from(MarketDispute dispute) {
        MarketDisputeDataObject dataObject = new MarketDisputeDataObject();
        dataObject.setDisputeId(dispute.getDisputeId());
        dataObject.setOrderId(dispute.getOrderId());
        dataObject.setGoodsType(dispute.getGoodsType());
        dataObject.setBuyerUserId(dispute.getBuyerUserId());
        dataObject.setSellerUserId(dispute.getSellerUserId());
        dataObject.setStatus(dispute.getStatus());
        dataObject.setReason(dispute.getReason());
        dataObject.setBuyerNote(dispute.getBuyerNote());
        dataObject.setSellerNote(dispute.getSellerNote());
        dataObject.setResolutionType(dispute.getResolutionType());
        dataObject.setResolvedBy(dispute.getResolvedBy());
        dataObject.setResolvedAt(dispute.getResolvedAt());
        dataObject.setCreateTime(dispute.getCreateTime());
        dataObject.setUpdateTime(dispute.getUpdateTime());
        return dataObject;
    }
}
