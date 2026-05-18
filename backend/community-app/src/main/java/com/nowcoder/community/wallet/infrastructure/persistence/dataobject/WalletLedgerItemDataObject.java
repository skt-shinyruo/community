package com.nowcoder.community.wallet.infrastructure.persistence.dataobject;

import com.nowcoder.community.wallet.domain.model.WalletLedgerItem;

import java.util.Date;
import java.util.UUID;

public class WalletLedgerItemDataObject {

    private UUID entryId;
    private UUID txnId;
    private UUID accountId;
    private String direction;
    private long entryAmount;
    private long balanceAfter;
    private Date entryCreateTime;
    private String requestId;
    private String txnType;
    private String bizType;
    private String bizId;
    private String status;
    private String remark;
    private UUID counterpartUserId;

    public WalletLedgerItem toDomain() {
        return new WalletLedgerItem(
                entryId,
                txnId,
                accountId,
                direction,
                entryAmount,
                balanceAfter,
                entryCreateTime,
                requestId,
                txnType,
                bizType,
                bizId,
                status,
                remark,
                counterpartUserId
        );
    }

    public UUID getEntryId() {
        return entryId;
    }

    public void setEntryId(UUID entryId) {
        this.entryId = entryId;
    }

    public UUID getTxnId() {
        return txnId;
    }

    public void setTxnId(UUID txnId) {
        this.txnId = txnId;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public void setAccountId(UUID accountId) {
        this.accountId = accountId;
    }

    public String getDirection() {
        return direction;
    }

    public void setDirection(String direction) {
        this.direction = direction;
    }

    public long getEntryAmount() {
        return entryAmount;
    }

    public void setEntryAmount(long entryAmount) {
        this.entryAmount = entryAmount;
    }

    public long getBalanceAfter() {
        return balanceAfter;
    }

    public void setBalanceAfter(long balanceAfter) {
        this.balanceAfter = balanceAfter;
    }

    public Date getEntryCreateTime() {
        return entryCreateTime;
    }

    public void setEntryCreateTime(Date entryCreateTime) {
        this.entryCreateTime = entryCreateTime;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getTxnType() {
        return txnType;
    }

    public void setTxnType(String txnType) {
        this.txnType = txnType;
    }

    public String getBizType() {
        return bizType;
    }

    public void setBizType(String bizType) {
        this.bizType = bizType;
    }

    public String getBizId() {
        return bizId;
    }

    public void setBizId(String bizId) {
        this.bizId = bizId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public UUID getCounterpartUserId() {
        return counterpartUserId;
    }

    public void setCounterpartUserId(UUID counterpartUserId) {
        this.counterpartUserId = counterpartUserId;
    }
}
