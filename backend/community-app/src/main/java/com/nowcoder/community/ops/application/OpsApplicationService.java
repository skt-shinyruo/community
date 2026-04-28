package com.nowcoder.community.ops.application;

import com.nowcoder.community.ops.application.command.ReindexSearchCommand;
import com.nowcoder.community.ops.application.result.SearchReindexResult;
import com.nowcoder.community.search.api.action.SearchReindexActionApi;
import org.springframework.stereotype.Service;

@Service
public class OpsApplicationService {

    private final SearchReindexActionApi searchReindexActionApi;

    public OpsApplicationService(SearchReindexActionApi searchReindexActionApi) {
        this.searchReindexActionApi = searchReindexActionApi;
    }

    public SearchReindexResult reindexSearch(ReindexSearchCommand command) {
        com.nowcoder.community.search.api.model.SearchReindexResult result = searchReindexActionApi.reindex();
        return new SearchReindexResult(result.jobId(), result.indexedCount(), result.skipped(), result.reason());
    }
}
