package com.nowcoder.community.search.infrastructure.api;

import com.nowcoder.community.search.api.action.SearchReindexActionApi;
import com.nowcoder.community.search.application.SearchReindexApplicationService;
import com.nowcoder.community.search.application.command.ReindexPostsCommand;
import com.nowcoder.community.search.application.result.SearchReindexResult;
import org.springframework.stereotype.Service;

@Service
public class SearchReindexActionApiAdapter implements SearchReindexActionApi {

    private final SearchReindexApplicationService searchReindexApplicationService;

    public SearchReindexActionApiAdapter(SearchReindexApplicationService searchReindexApplicationService) {
        this.searchReindexApplicationService = searchReindexApplicationService;
    }

    @Override
    public com.nowcoder.community.search.api.model.SearchReindexResult reindex() {
        SearchReindexResult result = searchReindexApplicationService.reindex(new ReindexPostsCommand());
        return new com.nowcoder.community.search.api.model.SearchReindexResult(
                result.jobId(),
                result.indexedCount(),
                result.skipped(),
                result.reason()
        );
    }
}
