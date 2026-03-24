package com.nowcoder.community.search.service;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class SearchReindexExecutionService {

    private final PostSearchService postSearchService;
    private final ReindexJobService reindexJobService;

    public SearchReindexExecutionService(PostSearchService postSearchService, ReindexJobService reindexJobService) {
        this.postSearchService = postSearchService;
        this.reindexJobService = reindexJobService;
    }

    public ExecutionResult execute() {
        ReindexJobService.ReindexJob job = reindexJobService.tryStart();
        if (job == null || !job.acquired()) {
            return new ExecutionResult(job == null ? null : job.jobId(), 0, true, skippedReason(job));
        }

        try {
            int count = postSearchService.clearAndReindexFromContentService();
            return new ExecutionResult(job.jobId(), count, false, null);
        } finally {
            reindexJobService.finish(job.jobId());
        }
    }

    private String skippedReason(ReindexJobService.ReindexJob job) {
        String jobId = job == null ? null : job.jobId();
        String suffix = StringUtils.hasText(jobId) ? " (jobId=" + jobId.trim() + ")" : "";
        return "reindex 任务正在执行" + suffix;
    }

    public record ExecutionResult(String jobId, int indexedCount, boolean skipped, String reason) {
    }
}
