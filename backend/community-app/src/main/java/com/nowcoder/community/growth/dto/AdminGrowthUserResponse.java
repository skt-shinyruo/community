package com.nowcoder.community.growth.dto;

import com.nowcoder.community.growth.entity.RewardLedgerEntry;

import java.util.ArrayList;
import java.util.List;

public class AdminGrowthUserResponse {

    private int userId;
    private String username;
    private String email;
    private int score;
    private int level;
    private int rewardBalance;
    private int frozenBalance;
    private List<RewardLedgerEntry> recentRewardLedgers = new ArrayList<>();

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

    public List<RewardLedgerEntry> getRecentRewardLedgers() {
        return recentRewardLedgers;
    }

    public void setRecentRewardLedgers(List<RewardLedgerEntry> recentRewardLedgers) {
        this.recentRewardLedgers = recentRewardLedgers == null ? new ArrayList<>() : recentRewardLedgers;
    }
}
