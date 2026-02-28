// 内部治理接口响应：返回用户当前禁言/封禁到期时间（null 表示未处罚）。
package com.nowcoder.community.user.api.dto;

import java.time.Instant;

public class InternalModerationStatusResponse {

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

