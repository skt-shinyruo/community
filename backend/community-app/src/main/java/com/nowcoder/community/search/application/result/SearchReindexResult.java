package com.nowcoder.community.search.application.result;

public record SearchReindexResult(String jobId, int indexedCount, boolean skipped, String reason) {
}
