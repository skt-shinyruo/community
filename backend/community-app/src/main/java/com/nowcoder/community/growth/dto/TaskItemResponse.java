package com.nowcoder.community.growth.dto;

public class TaskItemResponse {

    private String taskCode;
    private String taskType;
    private String periodType;
    private String periodKey;
    private int currentValue;
    private int targetValue;
    private String status;
    private int rewardGrowthDelta;
    private int rewardBalanceDelta;
    private boolean claimRequired;
    private int displayOrder;

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
}
