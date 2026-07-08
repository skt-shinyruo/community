package com.nowcoder.community.ops.application.result;

public record CompensationTriggerResult(
        String jobName,
        boolean accepted,
        int processedCount,
        int repairedCount,
        int skippedCount,
        String result,
        String message
) {
}
