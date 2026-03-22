package com.nowcoder.community.growth.dto;

public class CheckInStatusResponse {

    private boolean checkedInToday;
    private int currentStreak;
    private int maxStreak;
    private int totalCheckInDays;

    public boolean isCheckedInToday() {
        return checkedInToday;
    }

    public void setCheckedInToday(boolean checkedInToday) {
        this.checkedInToday = checkedInToday;
    }

    public int getCurrentStreak() {
        return currentStreak;
    }

    public void setCurrentStreak(int currentStreak) {
        this.currentStreak = currentStreak;
    }

    public int getMaxStreak() {
        return maxStreak;
    }

    public void setMaxStreak(int maxStreak) {
        this.maxStreak = maxStreak;
    }

    public int getTotalCheckInDays() {
        return totalCheckInDays;
    }

    public void setTotalCheckInDays(int totalCheckInDays) {
        this.totalCheckInDays = totalCheckInDays;
    }
}
