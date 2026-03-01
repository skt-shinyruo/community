package com.nowcoder.community.social.follow.dto;

import java.time.Instant;

public class FollowItem {

    private int targetId;
    private Instant followTime;

    public int getTargetId() {
        return targetId;
    }

    public void setTargetId(int targetId) {
        this.targetId = targetId;
    }

    public Instant getFollowTime() {
        return followTime;
    }

    public void setFollowTime(Instant followTime) {
        this.followTime = followTime;
    }
}

