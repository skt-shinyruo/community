package com.nowcoder.community.growth.application.result;

public class UserLevelConfigResult {

    private int windowDays;
    private int lv2SignInDays;
    private int lv3SignInDays;
    private boolean enabled;

    public int getWindowDays() {
        return windowDays;
    }

    public void setWindowDays(int windowDays) {
        this.windowDays = windowDays;
    }

    public int getLv2SignInDays() {
        return lv2SignInDays;
    }

    public void setLv2SignInDays(int lv2SignInDays) {
        this.lv2SignInDays = lv2SignInDays;
    }

    public int getLv3SignInDays() {
        return lv3SignInDays;
    }

    public void setLv3SignInDays(int lv3SignInDays) {
        this.lv3SignInDays = lv3SignInDays;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
