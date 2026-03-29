package com.nowcoder.community.search.api.model;

public record SearchReindexResult(String jobId, int indexedCount, boolean skipped, String reason) {
}
