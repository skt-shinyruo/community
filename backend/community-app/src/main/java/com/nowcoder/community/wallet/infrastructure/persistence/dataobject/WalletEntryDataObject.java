package com.nowcoder.community.wallet.infrastructure.persistence.dataobject;

import com.nowcoder.community.wallet.domain.model.WalletEntry;

public class WalletEntryDataObject extends WalletEntry {

    public static WalletEntryDataObject from(WalletEntry entry) {
        WalletEntryDataObject dataObject = new WalletEntryDataObject();
        dataObject.setEntryId(entry.getEntryId());
        dataObject.setTxnId(entry.getTxnId());
        dataObject.setAccountId(entry.getAccountId());
        dataObject.setDirection(entry.getDirection());
        dataObject.setAmount(entry.getAmount());
        dataObject.setBalanceAfter(entry.getBalanceAfter());
        dataObject.setCreateTime(entry.getCreateTime());
        return dataObject;
    }
}
