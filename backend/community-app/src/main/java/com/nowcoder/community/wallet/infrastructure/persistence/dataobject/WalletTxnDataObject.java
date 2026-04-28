package com.nowcoder.community.wallet.infrastructure.persistence.dataobject;

import com.nowcoder.community.wallet.domain.model.WalletTxn;

public class WalletTxnDataObject extends WalletTxn {

    public static WalletTxnDataObject from(WalletTxn txn) {
        WalletTxnDataObject dataObject = new WalletTxnDataObject();
        dataObject.setTxnId(txn.getTxnId());
        dataObject.setRequestId(txn.getRequestId());
        dataObject.setTxnType(txn.getTxnType());
        dataObject.setBizType(txn.getBizType());
        dataObject.setBizId(txn.getBizId());
        dataObject.setStatus(txn.getStatus());
        dataObject.setAmount(txn.getAmount());
        dataObject.setRemark(txn.getRemark());
        dataObject.setCreateTime(txn.getCreateTime());
        dataObject.setUpdateTime(txn.getUpdateTime());
        return dataObject;
    }
}
