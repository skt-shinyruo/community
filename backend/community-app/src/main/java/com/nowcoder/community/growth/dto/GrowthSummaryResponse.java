package com.nowcoder.community.growth.dto;

public class GrowthSummaryResponse {

    private int userId;
    private int score;
    private int level;
    private int userLevel;
    private int signInDaysInWindow;
    private int windowDays;
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

    public int getUserLevel() {
        return userLevel;
    }

    public void setUserLevel(int userLevel) {
        this.userLevel = userLevel;
    }

    public int getSignInDaysInWindow() {
        return signInDaysInWindow;
    }

    public void setSignInDaysInWindow(int signInDaysInWindow) {
        this.signInDaysInWindow = signInDaysInWindow;
    }

    public int getWindowDays() {
        return windowDays;
    }

    public void setWindowDays(int windowDays) {
        this.windowDays = windowDays;
    }
}
