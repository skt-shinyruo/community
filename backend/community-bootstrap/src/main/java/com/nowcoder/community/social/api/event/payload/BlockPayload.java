package com.nowcoder.community.social.api.event.payload;

/**
 * 拉黑关系变更事件载荷：
 * - social-service 产生（block/unblock）
 * - content-service/message-service 消费并维护本地投影，用于写路径拦截（最终一致）
 */
public class BlockPayload {

    private Integer blockerUserId;
    private Integer blockedUserId;
    private Boolean blocked;

    public Integer getBlockerUserId() {
        return blockerUserId;
    }

    public void setBlockerUserId(Integer blockerUserId) {
        this.blockerUserId = blockerUserId;
    }

    public Integer getBlockedUserId() {
        return blockedUserId;
    }

    public void setBlockedUserId(Integer blockedUserId) {
        this.blockedUserId = blockedUserId;
    }

    public Boolean getBlocked() {
        return blocked;
    }

    public void setBlocked(Boolean blocked) {
        this.blocked = blocked;
    }
}

