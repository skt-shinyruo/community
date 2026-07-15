package com.nowcoder.community.wallet.infrastructure.persistence.dataobject;

import com.nowcoder.community.wallet.domain.model.WalletAccount;

import java.util.Date;
import java.util.UUID;

public class WalletAccountDataObject {

    private UUID accountId;
    private String ownerType;
    private UUID ownerId;
    private String accountType;
    private long balance;
    private String status;
    private long version;
    private Date createTime;
    private Date updateTime;

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

    public WalletAccount toDomain() {
        return WalletAccount.reconstitute(
                accountId,
                ownerType,
                ownerId,
                accountType,
                balance,
                status,
                version,
                createTime,
                updateTime
        );
    }

    public UUID getAccountId() {
        return accountId;
    }

    public void setAccountId(UUID accountId) {
        this.accountId = accountId;
    }

    public String getOwnerType() {
        return ownerType;
    }

    public void setOwnerType(String ownerType) {
        this.ownerType = ownerType;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(UUID ownerId) {
        this.ownerId = ownerId;
    }

    public String getAccountType() {
        return accountType;
    }

    public void setAccountType(String accountType) {
        this.accountType = accountType;
    }

    public long getBalance() {
        return balance;
    }

    public void setBalance(long balance) {
        this.balance = balance;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public long getVersion() {
        return version;
    }

    public void setVersion(long version) {
        this.version = version;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    public Date getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime) {
        this.updateTime = updateTime;
    }
}
