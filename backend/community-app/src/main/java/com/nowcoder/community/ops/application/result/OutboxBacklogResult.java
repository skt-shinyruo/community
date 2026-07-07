package com.nowcoder.community.ops.application.result;

public record OutboxBacklogResult(String topic, String status, long count) {
}
