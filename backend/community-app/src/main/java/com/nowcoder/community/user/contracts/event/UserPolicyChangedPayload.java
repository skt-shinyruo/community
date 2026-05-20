package com.nowcoder.community.user.contracts.event;

import java.util.UUID;

public class UserPolicyChangedPayload {

    private UUID userId;
    private boolean userExists;
    private boolean suspended;
    private boolean muted;
    private Long muteUntil;
    private Long banUntil;
    private boolean canSendPrivate;
    private long occurredAtEpochMillis;
    private Long version;

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public boolean isUserExists() {
        return userExists;
    }

    public void setUserExists(boolean userExists) {
        this.userExists = userExists;
    }

    public boolean isSuspended() {
        return suspended;
    }

    public void setSuspended(boolean suspended) {
        this.suspended = suspended;
    }

    public boolean isMuted() {
        return muted;
    }

    public void setMuted(boolean muted) {
        this.muted = muted;
    }

    public Long getMuteUntil() {
        return muteUntil;
    }

    public void setMuteUntil(Long muteUntil) {
        this.muteUntil = muteUntil;
    }

    public Long getBanUntil() {
        return banUntil;
    }

    public void setBanUntil(Long banUntil) {
        this.banUntil = banUntil;
    }

    public boolean isCanSendPrivate() {
        return canSendPrivate;
    }

    public void setCanSendPrivate(boolean canSendPrivate) {
        this.canSendPrivate = canSendPrivate;
    }

    public long getOccurredAtEpochMillis() {
        return occurredAtEpochMillis;
    }

    public void setOccurredAtEpochMillis(long occurredAtEpochMillis) {
        this.occurredAtEpochMillis = occurredAtEpochMillis;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
}
