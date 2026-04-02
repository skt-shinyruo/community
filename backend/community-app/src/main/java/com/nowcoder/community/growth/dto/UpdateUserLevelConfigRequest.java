package com.nowcoder.community.growth.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public class UpdateUserLevelConfigRequest {

    @Positive
    private int windowDays;

    @Positive
    private int lv2SignInDays;

    @Positive
    private int lv3SignInDays;

    @NotNull
    private Boolean enabled;

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

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }
}
