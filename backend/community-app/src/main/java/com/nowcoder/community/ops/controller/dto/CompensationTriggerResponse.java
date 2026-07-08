package com.nowcoder.community.ops.controller.dto;

public record CompensationTriggerResponse(
        String jobName,
        boolean accepted,
        int processedCount,
        int repairedCount,
        int skippedCount,
        String result,
        String message
) {
}
