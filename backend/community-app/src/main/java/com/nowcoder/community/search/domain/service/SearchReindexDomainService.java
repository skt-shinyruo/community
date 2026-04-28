package com.nowcoder.community.search.domain.service;

import org.springframework.stereotype.Service;

@Service
public class SearchReindexDomainService {

    public int normalizeScanPageSize(int pageSize) {
        return Math.min(1000, Math.max(1, pageSize));
    }

    public String skippedReason(String jobId) {
        String suffix = hasText(jobId) ? " (jobId=" + jobId.trim() + ")" : "";
        return "reindex 任务正在执行" + suffix;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
