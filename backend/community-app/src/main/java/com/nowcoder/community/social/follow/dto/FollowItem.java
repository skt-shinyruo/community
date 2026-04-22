package com.nowcoder.community.social.follow.dto;

import java.time.Instant;
import java.util.UUID;

public class FollowItem {

    private UUID targetId;
    private Instant followTime;

    public UUID getTargetId() {
        return targetId;
    }

    public void setTargetId(UUID targetId) {
        this.targetId = targetId;
    }

    public Instant getFollowTime() {
        return followTime;
    }

    public void setFollowTime(Instant followTime) {
        this.followTime = followTime;
    }
}
