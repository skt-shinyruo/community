package com.nowcoder.community.social.contracts.event;

import java.time.Instant;
import java.util.UUID;

/**
 * 拉黑关系变更事件载荷：
 * - social 模块产生（block/unblock）
 * - content/message 模块消费并维护本地投影，用于写路径拦截（最终一致）
 */
public class BlockPayload {

    private UUID blockerUserId;
    private UUID blockedUserId;
    private Boolean blocked;
    private Instant occurredAt;
    private Long version;

    public UUID getBlockerUserId() {
        return blockerUserId;
    }

    public void setBlockerUserId(UUID blockerUserId) {
        this.blockerUserId = blockerUserId;
    }

    public UUID getBlockedUserId() {
        return blockedUserId;
    }

    public void setBlockedUserId(UUID blockedUserId) {
        this.blockedUserId = blockedUserId;
    }

    public Boolean getBlocked() {
        return blocked;
    }

    public void setBlocked(Boolean blocked) {
        this.blocked = blocked;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(Instant occurredAt) {
        this.occurredAt = occurredAt;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
}
