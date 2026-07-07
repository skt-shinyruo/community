package com.nowcoder.community.ops.controller.dto;

public record OutboxBacklogResponse(String topic, String status, long count) {
}
