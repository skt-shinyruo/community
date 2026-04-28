package com.nowcoder.community.search.application;

import com.nowcoder.community.search.application.command.ReindexPostsCommand;
import com.nowcoder.community.search.application.result.SearchReindexResult;
import org.springframework.stereotype.Service;

@Service
public class SearchAdminApplicationService {

    private final SearchReindexApplicationService searchReindexApplicationService;
    private final ReindexJobApplicationService reindexJobApplicationService;

    public SearchAdminApplicationService(
            SearchReindexApplicationService searchReindexApplicationService,
            ReindexJobApplicationService reindexJobApplicationService
    ) {
        this.searchReindexApplicationService = searchReindexApplicationService;
        this.reindexJobApplicationService = reindexJobApplicationService;
    }

    public SearchReindexResult reindex() {
        SearchReindexResult result = searchReindexApplicationService.reindex(new ReindexPostsCommand());
        if (result.skipped()) {
            reindexJobApplicationService.conflict(result.jobId());
        }
        return result;
    }
}
