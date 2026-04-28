package com.nowcoder.community.wallet.infrastructure.persistence.dataobject;

import com.nowcoder.community.wallet.domain.model.TransferOrder;

public class TransferOrderDataObject extends TransferOrder {

    public static TransferOrderDataObject from(TransferOrder order) {
        TransferOrderDataObject dataObject = new TransferOrderDataObject();
        dataObject.setOrderId(order.getOrderId());
        dataObject.setRequestId(order.getRequestId());
        dataObject.setFromUserId(order.getFromUserId());
        dataObject.setToUserId(order.getToUserId());
        dataObject.setAmount(order.getAmount());
        dataObject.setStatus(order.getStatus());
        dataObject.setRemark(order.getRemark());
        dataObject.setCreateTime(order.getCreateTime());
        dataObject.setUpdateTime(order.getUpdateTime());
        return dataObject;
    }
}
