package com.nowcoder.community.content.domain.event;

import java.util.UUID;

/**
 * 帖子领域事件：内容/状态/分数发生变化。
 *
 * <p>注意：该事件仅承载最小标识（postId），payload 由 assembler 在事务内构造。</p>
 */
public record PostUpdatedDomainEvent(UUID postId) {
}
