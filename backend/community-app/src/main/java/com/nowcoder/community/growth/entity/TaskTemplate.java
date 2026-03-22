package com.nowcoder.community.growth.entity;

import java.util.Date;

public class TaskTemplate {

    private String taskCode;
    private String taskType;
    private String periodType;
    private String triggerEventType;
    private int targetValue;
    private int rewardGrowthDelta;
    private int rewardBalanceDelta;
    private boolean claimRequired;
    private int displayOrder;
    private String status;
    private Date createTime;
    private Date updateTime;

    public String getTaskCode() {
        return taskCode;
    }

    public void setTaskCode(String taskCode) {
        this.taskCode = taskCode;
    }

    public String getTaskType() {
        return taskType;
    }

    public void setTaskType(String taskType) {
        this.taskType = taskType;
    }

    public String getPeriodType() {
        return periodType;
    }

    public void setPeriodType(String periodType) {
        this.periodType = periodType;
    }

    public String getTriggerEventType() {
        return triggerEventType;
    }

    public void setTriggerEventType(String triggerEventType) {
        this.triggerEventType = triggerEventType;
    }

    public int getTargetValue() {
        return targetValue;
    }

    public void setTargetValue(int targetValue) {
        this.targetValue = targetValue;
    }

    public int getRewardGrowthDelta() {
        return rewardGrowthDelta;
    }

    public void setRewardGrowthDelta(int rewardGrowthDelta) {
        this.rewardGrowthDelta = rewardGrowthDelta;
    }

    public int getRewardBalanceDelta() {
        return rewardBalanceDelta;
    }

    public void setRewardBalanceDelta(int rewardBalanceDelta) {
        this.rewardBalanceDelta = rewardBalanceDelta;
    }

    public boolean isClaimRequired() {
        return claimRequired;
    }

    public void setClaimRequired(boolean claimRequired) {
        this.claimRequired = claimRequired;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }

    public void setDisplayOrder(int displayOrder) {
        this.displayOrder = displayOrder;
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

    public Date getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime) {
        this.updateTime = updateTime;
    }
}
