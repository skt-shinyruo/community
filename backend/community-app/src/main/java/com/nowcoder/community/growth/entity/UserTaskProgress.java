package com.nowcoder.community.growth.entity;

import java.util.Date;
import java.util.UUID;

public class UserTaskProgress {

    private UUID id;
    private UUID userId;
    private String taskCode;
    private String periodKey;
    private int currentValue;
    private int targetValue;
    private String status;
    private Date reachedAt;
    private Date claimedAt;
    private String rewardGrantId;
    private String lastSourceEventId;
    private Date updateTime;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public String getTaskCode() {
        return taskCode;
    }

    public void setTaskCode(String taskCode) {
        this.taskCode = taskCode;
    }

    public String getPeriodKey() {
        return periodKey;
    }

    public void setPeriodKey(String periodKey) {
        this.periodKey = periodKey;
    }

    public int getCurrentValue() {
        return currentValue;
    }

    public void setCurrentValue(int currentValue) {
        this.currentValue = currentValue;
    }

    public int getTargetValue() {
        return targetValue;
    }

    public void setTargetValue(int targetValue) {
        this.targetValue = targetValue;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Date getReachedAt() {
        return reachedAt;
    }

    public void setReachedAt(Date reachedAt) {
        this.reachedAt = reachedAt;
    }

    public Date getClaimedAt() {
        return claimedAt;
    }

    public void setClaimedAt(Date claimedAt) {
        this.claimedAt = claimedAt;
    }

    public String getRewardGrantId() {
        return rewardGrantId;
    }

    public void setRewardGrantId(String rewardGrantId) {
        this.rewardGrantId = rewardGrantId;
    }

    public String getLastSourceEventId() {
        return lastSourceEventId;
    }

    public void setLastSourceEventId(String lastSourceEventId) {
        this.lastSourceEventId = lastSourceEventId;
    }

    public Date getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime) {
        this.updateTime = updateTime;
    }
}
