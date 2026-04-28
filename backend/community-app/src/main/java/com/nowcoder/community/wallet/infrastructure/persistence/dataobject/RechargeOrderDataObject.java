package com.nowcoder.community.wallet.infrastructure.persistence.dataobject;

import com.nowcoder.community.wallet.domain.model.RechargeOrder;

public class RechargeOrderDataObject extends RechargeOrder {

    public static RechargeOrderDataObject from(RechargeOrder order) {
        RechargeOrderDataObject dataObject = new RechargeOrderDataObject();
        dataObject.setOrderId(order.getOrderId());
        dataObject.setRequestId(order.getRequestId());
        dataObject.setUserId(order.getUserId());
        dataObject.setAmount(order.getAmount());
        dataObject.setStatus(order.getStatus());
        dataObject.setChannel(order.getChannel());
        dataObject.setChannelOrderId(order.getChannelOrderId());
        dataObject.setRemark(order.getRemark());
        dataObject.setCreateTime(order.getCreateTime());
        dataObject.setUpdateTime(order.getUpdateTime());
        return dataObject;
    }
}
