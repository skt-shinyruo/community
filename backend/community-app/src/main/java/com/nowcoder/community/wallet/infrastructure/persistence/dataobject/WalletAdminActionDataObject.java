package com.nowcoder.community.wallet.infrastructure.persistence.dataobject;

import com.nowcoder.community.wallet.domain.model.WalletAdminAction;

public class WalletAdminActionDataObject extends WalletAdminAction {

    public static WalletAdminActionDataObject from(WalletAdminAction action) {
        WalletAdminActionDataObject dataObject = new WalletAdminActionDataObject();
        dataObject.setActionId(action.getActionId());
        dataObject.setRequestId(action.getRequestId());
        dataObject.setActorUserId(action.getActorUserId());
        dataObject.setTargetAccountId(action.getTargetAccountId());
        dataObject.setActionType(action.getActionType());
        dataObject.setAmount(action.getAmount());
        dataObject.setRemark(action.getRemark());
        dataObject.setCreateTime(action.getCreateTime());
        return dataObject;
    }
}
