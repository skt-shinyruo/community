package com.nowcoder.community.search.service;

import com.nowcoder.community.search.api.action.SearchReindexActionApi;
import com.nowcoder.community.search.api.model.SearchReindexResult;
import com.nowcoder.community.search.dto.SearchReindexResponse;
import org.springframework.stereotype.Service;

@Service
public class SearchAdminService {

    private final SearchReindexActionApi searchReindexActionApi;
    private final ReindexJobService reindexJobService;

    public SearchAdminService(SearchReindexActionApi searchReindexActionApi,
                              ReindexJobService reindexJobService) {
        this.searchReindexActionApi = searchReindexActionApi;
        this.reindexJobService = reindexJobService;
    }

    public SearchReindexResponse reindex() {
        SearchReindexResult result = searchReindexActionApi.reindex();
        if (result.skipped()) {
            reindexJobService.conflict(result.jobId());
        }

        SearchReindexResponse response = new SearchReindexResponse();
        response.setJobId(result.jobId());
        response.setIndexedCount(result.indexedCount());
        return response;
    }
}
