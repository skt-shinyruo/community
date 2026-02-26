package com.nowcoder.community.user.api.rpc.dto;

import java.io.Serializable;
import java.time.Instant;

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

