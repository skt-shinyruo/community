package com.nowcoder.community.growth.dto;

public class GrowthSummaryResponse {

    private int userId;
    private int score;
    private int level;
    private int rewardBalance;
    private int frozenBalance;

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public int getScore() {
        return score;
    }

    public void setScore(int score) {
        this.score = score;
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public int getRewardBalance() {
        return rewardBalance;
    }

    public void setRewardBalance(int rewardBalance) {
        this.rewardBalance = rewardBalance;
    }

    public int getFrozenBalance() {
        return frozenBalance;
    }

    public void setFrozenBalance(int frozenBalance) {
        this.frozenBalance = frozenBalance;
    }
}
