package com.nowcoder.community.social.contracts.event;

import java.time.Instant;
import java.util.UUID;

/**
 * 拉黑关系变更事件载荷：
 * - social 模块产生（block/unblock）
 * - content/message 模块消费并维护本地投影，用于写路径拦截（最终一致）
 */
public record BlockPayload(
        UUID blockerUserId,
        UUID blockedUserId,
        Boolean blocked,
        Instant occurredAt,
        Long version
) {
}
