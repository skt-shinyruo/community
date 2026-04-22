package com.nowcoder.community.growth.entity;

import java.util.Date;
import java.util.UUID;

public class UserLevelRuleConfig {

    private UUID id;
    private int windowDays;
    private int lv2SignInDays;
    private int lv3SignInDays;
    private boolean enabled;
    private UUID updatedBy;
    private Date updateTime;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
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

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public UUID getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(UUID updatedBy) {
        this.updatedBy = updatedBy;
    }

    public Date getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime) {
        this.updateTime = updateTime;
    }
}
