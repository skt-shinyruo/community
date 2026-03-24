package com.nowcoder.community.search.service;

import com.nowcoder.community.search.dto.SearchReindexResponse;
import org.springframework.stereotype.Service;

@Service
public class SearchAdminService {

    private final SearchReindexExecutionService searchReindexExecutionService;
    private final ReindexJobService reindexJobService;

    public SearchAdminService(SearchReindexExecutionService searchReindexExecutionService,
                              ReindexJobService reindexJobService) {
        this.searchReindexExecutionService = searchReindexExecutionService;
        this.reindexJobService = reindexJobService;
    }

    public SearchReindexResponse reindex() {
        SearchReindexExecutionService.ExecutionResult result = searchReindexExecutionService.execute();
        if (result.skipped()) {
            reindexJobService.conflict(result.jobId());
        }

        SearchReindexResponse response = new SearchReindexResponse();
        response.setJobId(result.jobId());
        response.setIndexedCount(result.indexedCount());
        return response;
    }
}
