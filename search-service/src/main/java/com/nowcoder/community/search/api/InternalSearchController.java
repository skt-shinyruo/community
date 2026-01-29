package com.nowcoder.community.search.api;

import com.nowcoder.community.common.api.Result;
import com.nowcoder.community.search.api.dto.ReindexResponse;
import com.nowcoder.community.search.service.PostSearchService;
import com.nowcoder.community.search.service.ReindexJobService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/search")
public class InternalSearchController {

    private final PostSearchService postSearchService;
    private final ReindexJobService reindexJobService;

    public InternalSearchController(PostSearchService postSearchService, ReindexJobService reindexJobService) {
        this.postSearchService = postSearchService;
        this.reindexJobService = reindexJobService;
    }

    @PostMapping("/reindex")
    public Result<ReindexResponse> reindex() {
        ReindexJobService.ReindexJob job = reindexJobService.tryStart();
        if (job == null || !job.acquired()) {
            reindexJobService.conflict(job == null ? null : job.jobId());
        }
        try {
            int count = postSearchService.clearAndReindexFromContentService();
            ReindexResponse resp = new ReindexResponse();
            resp.setJobId(job.jobId());
            resp.setIndexedCount(count);
            return Result.ok(resp);
        } finally {
            reindexJobService.finish(job.jobId());
        }
    }
}
