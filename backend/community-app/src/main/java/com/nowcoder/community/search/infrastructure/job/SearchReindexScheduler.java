package com.nowcoder.community.search.infrastructure.job;

import com.nowcoder.community.common.trace.TraceJobRunner;
import com.nowcoder.community.search.application.SearchReindexApplicationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class SearchReindexScheduler {

    static final String JOB_NAME = "searchReindex";

    private static final Logger log = LoggerFactory.getLogger(SearchReindexScheduler.class);

    private final SearchReindexApplicationService applicationService;

    public SearchReindexScheduler(SearchReindexApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @Scheduled(cron = "${search.reindex.cron:-}")
    public void reindex() {
        TraceJobRunner.run(JOB_NAME, () -> {
            try {
                SearchReindexApplicationService.ReindexResult result = applicationService.reindex();
                String message;
                if (result.skipped()) {
                    message = "[search-reindex] skipped executionId=" + result.executionId()
                            + " reason=" + result.reason();
                } else {
                    message = "[search-reindex] completed executionId=" + result.executionId()
                            + " indexedCount=" + result.indexedCount();
                }
                log.info(message);
            } catch (RuntimeException failure) {
                log.warn("[search-reindex] failed", failure);
            }
        });
    }
}
