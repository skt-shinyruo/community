package com.nowcoder.community.market.infrastructure.persistence.dataobject;

import com.nowcoder.community.market.domain.model.MarketWalletAction;

public class MarketWalletActionDataObject extends MarketWalletAction {

    public static MarketWalletActionDataObject from(MarketWalletAction action) {
        MarketWalletActionDataObject dataObject = new MarketWalletActionDataObject();
        dataObject.setActionId(action.getActionId());
        dataObject.setOrderId(action.getOrderId());
        dataObject.setDisputeId(action.getDisputeId());
        dataObject.setActionType(action.getActionType());
        dataObject.setRequestId(action.getRequestId());
        dataObject.setWalletBizId(action.getWalletBizId());
        dataObject.setActorUserId(action.getActorUserId());
        dataObject.setCounterpartyUserId(action.getCounterpartyUserId());
        dataObject.setAmount(action.getAmount());
        dataObject.setStatus(action.getStatus());
        dataObject.setResultType(action.getResultType());
        dataObject.setWalletTxnId(action.getWalletTxnId());
        dataObject.setFailureCode(action.getFailureCode());
        dataObject.setLastError(action.getLastError());
        dataObject.setRetryCount(action.getRetryCount());
        dataObject.setNextRetryAt(action.getNextRetryAt());
        dataObject.setProcessingLeaseUntil(action.getProcessingLeaseUntil());
        dataObject.setCreateTime(action.getCreateTime());
        dataObject.setUpdateTime(action.getUpdateTime());
        return dataObject;
    }
}
