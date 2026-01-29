package com.nowcoder.community.common.event;

/**
 * 事件治理：当消费端遇到不支持的 envelope version 或未知 event type 时的处理策略。
 *
 * <p>说明：
 * - SKIP：跳过本条消息（仍会 ack/提交 offset），必须配合日志/指标保证可观测性。
 * - DLQ：抛出异常交给 Kafka error handler（重试/DLQ），用于 fail-closed。
 */
public enum UnknownEventAction {
    SKIP,
    DLQ;

    public static UnknownEventAction parseOrDefault(String raw, UnknownEventAction defaultValue) {
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        try {
            return UnknownEventAction.valueOf(raw.trim().toUpperCase());
        } catch (Exception ignored) {
            return defaultValue;
        }
    }
}

