package com.nowcoder.community.growth.entity;

import java.util.Date;
import java.util.UUID;

public class RewardGrantRecord {

    private UUID id;
    private String grantId;
    private UUID userId;
    private String grantType;
    private String sourceEventId;
    private String sourceEventType;
    private int growthDelta;
    private int rewardDelta;
    private String status;
    private Date createTime;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getGrantId() {
        return grantId;
    }

    public void setGrantId(String grantId) {
        this.grantId = grantId;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public String getGrantType() {
        return grantType;
    }

    public void setGrantType(String grantType) {
        this.grantType = grantType;
    }

    public String getSourceEventId() {
        return sourceEventId;
    }

    public void setSourceEventId(String sourceEventId) {
        this.sourceEventId = sourceEventId;
    }

    public String getSourceEventType() {
        return sourceEventType;
    }

    public void setSourceEventType(String sourceEventType) {
        this.sourceEventType = sourceEventType;
    }

    public int getGrowthDelta() {
        return growthDelta;
    }

    public void setGrowthDelta(int growthDelta) {
        this.growthDelta = growthDelta;
    }

    public int getRewardDelta() {
        return rewardDelta;
    }

    public void setRewardDelta(int rewardDelta) {
        this.rewardDelta = rewardDelta;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }
}
