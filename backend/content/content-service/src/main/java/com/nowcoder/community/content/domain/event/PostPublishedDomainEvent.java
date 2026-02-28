package com.nowcoder.community.content.domain.event;

/**
 * 帖子领域事件：发布成功（写入完成）。
 *
 * <p>注意：该事件仅承载最小标识（postId），payload 由 assembler 在事务内构造。</p>
 */
public record PostPublishedDomainEvent(int postId) {
}

