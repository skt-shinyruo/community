package com.nowcoder.community.search.service;

import com.nowcoder.community.search.dto.SearchReindexResponse;
import org.springframework.stereotype.Service;

@Service
public class SearchAdminService {

    private final PostSearchService postSearchService;
    private final ReindexJobService reindexJobService;

    public SearchAdminService(PostSearchService postSearchService, ReindexJobService reindexJobService) {
        this.postSearchService = postSearchService;
        this.reindexJobService = reindexJobService;
    }

    public SearchReindexResponse reindex() {
        ReindexJobService.ReindexJob job = reindexJobService.tryStart();
        if (job == null || !job.acquired()) {
            reindexJobService.conflict(job == null ? null : job.jobId());
        }

        try {
            int count = postSearchService.clearAndReindexFromContentService();
            SearchReindexResponse response = new SearchReindexResponse();
            response.setJobId(job.jobId());
            response.setIndexedCount(count);
            return response;
        } finally {
            reindexJobService.finish(job.jobId());
        }
    }
}
