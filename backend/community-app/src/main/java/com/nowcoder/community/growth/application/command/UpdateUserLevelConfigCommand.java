package com.nowcoder.community.growth.application.command;

import java.util.UUID;

public class UpdateUserLevelConfigCommand {

    private UUID actorUserId;

    private int windowDays;

    private int lv2SignInDays;

    private int lv3SignInDays;

    private Boolean enabled;

    public UUID getActorUserId() {
        return actorUserId;
    }

    public void setActorUserId(UUID actorUserId) {
        this.actorUserId = actorUserId;
    }

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
