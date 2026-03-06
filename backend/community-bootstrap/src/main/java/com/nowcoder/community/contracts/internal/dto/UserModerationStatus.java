package com.nowcoder.community.contracts.internal.dto;

import java.io.Serializable;
import java.time.Instant;

/**
 * 用户治理状态（禁言/封禁截止时间）。
 *
 * <p>放在 contracts-core 的 internal DTO 里，目的是让下游在写路径做校验时不必依赖 user-api 模块，避免领域间形成编译期环。</p>
 */
public class UserModerationStatus implements Serializable {

    private int userId;
    private Instant muteUntil;
    private Instant banUntil;

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public Instant getMuteUntil() {
        return muteUntil;
    }

    public void setMuteUntil(Instant muteUntil) {
        this.muteUntil = muteUntil;
    }

    public Instant getBanUntil() {
        return banUntil;
    }

    public void setBanUntil(Instant banUntil) {
        this.banUntil = banUntil;
    }
}
