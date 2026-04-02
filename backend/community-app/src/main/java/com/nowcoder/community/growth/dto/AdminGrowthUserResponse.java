package com.nowcoder.community.growth.dto;

import java.util.ArrayList;
import java.util.List;

public class AdminGrowthUserResponse {

    private int userId;
    private String username;
    private String email;
    private int score;
    private int level;
    private int userLevel;
    private int signInDaysInWindow;
    private int windowDays;
    private int rewardBalance;
    private int frozenBalance;
    private List<RewardLedgerEntryResponse> recentRewardLedgers = new ArrayList<>();

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
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

    public List<RewardLedgerEntryResponse> getRecentRewardLedgers() {
        return recentRewardLedgers;
    }

    public void setRecentRewardLedgers(List<RewardLedgerEntryResponse> recentRewardLedgers) {
        this.recentRewardLedgers = recentRewardLedgers == null ? new ArrayList<>() : recentRewardLedgers;
    }
}
