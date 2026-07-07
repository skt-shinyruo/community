package com.nowcoder.community.common.outbox;

public record OutboxBacklogRow(String topic, String status, long count) {
}
