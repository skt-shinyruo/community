package com.nowcoder.community.common.event.payload;

import java.time.Instant;

/**
 * 用户处罚状态变更事件载荷：
 * - user-service 产生（处罚状态为 user 表的 mute_until/ban_until）
 * - content-service/message-service 消费并维护本地投影，用于写路径拦截（最终一致）
 */
public class ModerationStatusPayload {

    private Integer userId;
    private Instant muteUntil;
    private Instant banUntil;

    public Integer getUserId() {
        return userId;
    }

    public void setUserId(Integer userId) {
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

