package com.nowcoder.community.growth.dto;

public class GrowthSummaryResponse {

    private int userId;
    private int score;
    private int level;
    private boolean userLevelEnabled;
    private Integer userLevel;
    private Integer signInDaysInWindow;
    private Integer windowDays;
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

    public boolean isUserLevelEnabled() {
        return userLevelEnabled;
    }

    public void setUserLevelEnabled(boolean userLevelEnabled) {
        this.userLevelEnabled = userLevelEnabled;
    }

    public Integer getUserLevel() {
        return userLevel;
    }

    public void setUserLevel(Integer userLevel) {
        this.userLevel = userLevel;
    }

    public Integer getSignInDaysInWindow() {
        return signInDaysInWindow;
    }

    public void setSignInDaysInWindow(Integer signInDaysInWindow) {
        this.signInDaysInWindow = signInDaysInWindow;
    }

    public Integer getWindowDays() {
        return windowDays;
    }

    public void setWindowDays(Integer windowDays) {
        this.windowDays = windowDays;
    }
}
