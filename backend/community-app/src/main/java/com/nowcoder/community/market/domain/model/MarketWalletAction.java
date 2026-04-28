package com.nowcoder.community.market.domain.model;

import java.util.Date;
import java.util.UUID;

public class MarketWalletAction {

    private UUID actionId;
    private UUID orderId;
    private UUID disputeId;
    private String actionType;
    private String requestId;
    private String walletBizId;
    private UUID actorUserId;
    private UUID counterpartyUserId;
    private long amount;
    private String status;
    private String resultType;
    private UUID walletTxnId;
    private String failureCode;
    private String lastError;
    private int retryCount;
    private Date nextRetryAt;
    private Date processingLeaseUntil;
    private Date createTime;
    private Date updateTime;

    public UUID getActionId() {
        return actionId;
    }

    public void setActionId(UUID actionId) {
        this.actionId = actionId;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public void setOrderId(UUID orderId) {
        this.orderId = orderId;
    }

    public UUID getDisputeId() {
        return disputeId;
    }

    public void setDisputeId(UUID disputeId) {
        this.disputeId = disputeId;
    }

    public String getActionType() {
        return actionType;
    }

    public void setActionType(String actionType) {
        this.actionType = actionType;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getWalletBizId() {
        return walletBizId;
    }

    public void setWalletBizId(String walletBizId) {
        this.walletBizId = walletBizId;
    }

    public UUID getActorUserId() {
        return actorUserId;
    }

    public void setActorUserId(UUID actorUserId) {
        this.actorUserId = actorUserId;
    }

    public UUID getCounterpartyUserId() {
        return counterpartyUserId;
    }

    public void setCounterpartyUserId(UUID counterpartyUserId) {
        this.counterpartyUserId = counterpartyUserId;
    }

    public long getAmount() {
        return amount;
    }

    public void setAmount(long amount) {
        this.amount = amount;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getResultType() {
        return resultType;
    }

    public void setResultType(String resultType) {
        this.resultType = resultType;
    }

    public UUID getWalletTxnId() {
        return walletTxnId;
    }

    public void setWalletTxnId(UUID walletTxnId) {
        this.walletTxnId = walletTxnId;
    }

    public String getFailureCode() {
        return failureCode;
    }

    public void setFailureCode(String failureCode) {
        this.failureCode = failureCode;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
    }

    public Date getNextRetryAt() {
        return nextRetryAt;
    }

    public void setNextRetryAt(Date nextRetryAt) {
        this.nextRetryAt = nextRetryAt;
    }

    public Date getProcessingLeaseUntil() {
        return processingLeaseUntil;
    }

    public void setProcessingLeaseUntil(Date processingLeaseUntil) {
        this.processingLeaseUntil = processingLeaseUntil;
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
