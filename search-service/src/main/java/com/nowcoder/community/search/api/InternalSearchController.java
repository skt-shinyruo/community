package com.nowcoder.community.search.api;

import com.nowcoder.community.common.api.Result;
import com.nowcoder.community.search.api.dto.ReindexResponse;
import com.nowcoder.community.search.service.PostSearchService;
import com.nowcoder.community.search.service.ReindexJobService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/search")
public class InternalSearchController {

    private static final Logger log = LoggerFactory.getLogger(InternalSearchController.class);
    private static final String HEADER_EXTERNAL_PATH = "X-External-Path";

    private final PostSearchService postSearchService;
    private final ReindexJobService reindexJobService;

    public InternalSearchController(PostSearchService postSearchService, ReindexJobService reindexJobService) {
        this.postSearchService = postSearchService;
        this.reindexJobService = reindexJobService;
    }

    @PostMapping("/reindex")
    public Result<ReindexResponse> reindex(
            @RequestHeader(value = HEADER_EXTERNAL_PATH, required = false) String externalPath
    ) {
        if ("/api/search/internal/reindex".equals(externalPath)) {
            // 该路径属于历史遗留对外入口命名：保留兼容期，但需要引导迁移到 /api/ops/**。
            log.warn("[reindex] deprecated external path used: {}", externalPath);
        }
        ReindexJobService.ReindexJob job = reindexJobService.tryStart();
        if (job == null || !job.acquired()) {
            reindexJobService.conflict(job == null ? null : job.jobId());
        }
        AutoCloseable renewal = reindexJobService.startRenewal(job.jobId());
        try {
            int count = postSearchService.clearAndReindexFromContentService();
            ReindexResponse resp = new ReindexResponse();
            resp.setJobId(job.jobId());
            resp.setIndexedCount(count);
            return Result.ok(resp);
        } finally {
            try {
                if (renewal != null) {
                    renewal.close();
                }
            } catch (Exception ignored) {
            }
            reindexJobService.finish(job.jobId());
        }
    }
}
