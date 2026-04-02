package com.nowcoder.community.wallet.entity;

import java.util.Date;

public class WalletAdminAction {

    private long actionId;
    private String requestId;
    private long actorUserId;
    private long targetAccountId;
    private String actionType;
    private long amount;
    private String remark;
    private Date createTime;

    public long getActionId() {
        return actionId;
    }

    public void setActionId(long actionId) {
        this.actionId = actionId;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public long getActorUserId() {
        return actorUserId;
    }

    public void setActorUserId(long actorUserId) {
        this.actorUserId = actorUserId;
    }

    public long getTargetAccountId() {
        return targetAccountId;
    }

    public void setTargetAccountId(long targetAccountId) {
        this.targetAccountId = targetAccountId;
    }

    public String getActionType() {
        return actionType;
    }

    public void setActionType(String actionType) {
        this.actionType = actionType;
    }

    public long getAmount() {
        return amount;
    }

    public void setAmount(long amount) {
        this.amount = amount;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }
}
