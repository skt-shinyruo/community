package com.nowcoder.community.wallet.infrastructure.persistence.dataobject;

import com.nowcoder.community.wallet.domain.model.WithdrawOrder;

public class WithdrawOrderDataObject extends WithdrawOrder {

    public static WithdrawOrderDataObject from(WithdrawOrder order) {
        WithdrawOrderDataObject dataObject = new WithdrawOrderDataObject();
        dataObject.setOrderId(order.getOrderId());
        dataObject.setRequestId(order.getRequestId());
        dataObject.setUserId(order.getUserId());
        dataObject.setAmount(order.getAmount());
        dataObject.setStatus(order.getStatus());
        dataObject.setPayeeAccount(order.getPayeeAccount());
        dataObject.setFailureReason(order.getFailureReason());
        dataObject.setCreateTime(order.getCreateTime());
        dataObject.setUpdateTime(order.getUpdateTime());
        return dataObject;
    }
}
