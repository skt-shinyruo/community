package com.nowcoder.community.growth.entity;

import java.util.Date;

public class AdminRewardAdjustment {

    private long id;
    private int actorUserId;
    private int targetUserId;
    private String assetType;
    private int delta;
    private int beforeValue;
    private int afterValue;
    private String reason;
    private String confirmToken;
    private Date createTime;

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public int getActorUserId() {
        return actorUserId;
    }

    public void setActorUserId(int actorUserId) {
        this.actorUserId = actorUserId;
    }

    public int getTargetUserId() {
        return targetUserId;
    }

    public void setTargetUserId(int targetUserId) {
        this.targetUserId = targetUserId;
    }

    public String getAssetType() {
        return assetType;
    }

    public void setAssetType(String assetType) {
        this.assetType = assetType;
    }

    public int getDelta() {
        return delta;
    }

    public void setDelta(int delta) {
        this.delta = delta;
    }

    public int getBeforeValue() {
        return beforeValue;
    }

    public void setBeforeValue(int beforeValue) {
        this.beforeValue = beforeValue;
    }

    public int getAfterValue() {
        return afterValue;
    }

    public void setAfterValue(int afterValue) {
        this.afterValue = afterValue;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getConfirmToken() {
        return confirmToken;
    }

    public void setConfirmToken(String confirmToken) {
        this.confirmToken = confirmToken;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }
}
