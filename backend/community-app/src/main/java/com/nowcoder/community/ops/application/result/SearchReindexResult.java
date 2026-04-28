package com.nowcoder.community.ops.application.result;

public record SearchReindexResult(String jobId, int indexedCount, boolean skipped, String reason) {
}
