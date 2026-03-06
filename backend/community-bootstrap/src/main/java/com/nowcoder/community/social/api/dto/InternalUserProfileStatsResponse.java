package com.nowcoder.community.social.api.dto;

/**
 * social-service 内部聚合只读响应：
 * - 面向 user-service 等“聚合展示”场景，避免同一页面 fan-out 多次调用；
 * - 开发阶段 internal 接口默认放行；生产建议通过网络隔离/网关策略收敛暴露面，并避免对外暴露 /internal/**。
 */
public class InternalUserProfileStatsResponse {

    private long likeCount;
    private long followeeCount;
    private long followerCount;
    private boolean hasFollowed;

    /**
     * 预留字段：若未来做“部分字段降级/未知”可置为 true。
     * 当前实现由调用方（client）决定是否 fail-open，因此默认 false。
     */
    private boolean degraded;

    public long getLikeCount() {
        return likeCount;
    }

    public void setLikeCount(long likeCount) {
        this.likeCount = likeCount;
    }

    public long getFolloweeCount() {
        return followeeCount;
    }

    public void setFolloweeCount(long followeeCount) {
        this.followeeCount = followeeCount;
    }

    public long getFollowerCount() {
        return followerCount;
    }

    public void setFollowerCount(long followerCount) {
        this.followerCount = followerCount;
    }

    public boolean isHasFollowed() {
        return hasFollowed;
    }

    public void setHasFollowed(boolean hasFollowed) {
        this.hasFollowed = hasFollowed;
    }

    public boolean isDegraded() {
        return degraded;
    }

    public void setDegraded(boolean degraded) {
        this.degraded = degraded;
    }
}
