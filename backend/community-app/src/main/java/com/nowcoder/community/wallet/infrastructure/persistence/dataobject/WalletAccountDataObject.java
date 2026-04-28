package com.nowcoder.community.wallet.infrastructure.persistence.dataobject;

import com.nowcoder.community.wallet.domain.model.WalletAccount;

public class WalletAccountDataObject extends WalletAccount {

    public static WalletAccountDataObject from(WalletAccount account) {
        WalletAccountDataObject dataObject = new WalletAccountDataObject();
        dataObject.setAccountId(account.getAccountId());
        dataObject.setOwnerType(account.getOwnerType());
        dataObject.setOwnerId(account.getOwnerId());
        dataObject.setAccountType(account.getAccountType());
        dataObject.setBalance(account.getBalance());
        dataObject.setStatus(account.getStatus());
        dataObject.setVersion(account.getVersion());
        dataObject.setCreateTime(account.getCreateTime());
        dataObject.setUpdateTime(account.getUpdateTime());
        return dataObject;
    }
}
