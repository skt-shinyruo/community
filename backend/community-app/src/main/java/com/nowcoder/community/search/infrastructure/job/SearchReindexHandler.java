package com.nowcoder.community.search.infrastructure.job;

import com.nowcoder.community.common.trace.TraceJobRunner;
import com.nowcoder.community.search.application.SearchReindexApplicationService;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "search.storage", havingValue = "es")
public class SearchReindexHandler {

    static final String JOB_NAME = "searchReindex";

    private static final Logger log = LoggerFactory.getLogger(SearchReindexHandler.class);

    private final SearchReindexApplicationService applicationService;

    public SearchReindexHandler(SearchReindexApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @XxlJob(JOB_NAME)
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
                XxlJobHelper.log(message);
                XxlJobHelper.handleSuccess(message);
                log.info(message);
            } catch (RuntimeException failure) {
                String message = "[search-reindex] failed: " + failure;
                XxlJobHelper.log(failure);
                XxlJobHelper.handleFail(message);
                log.warn(message);
            }
        });
    }
}
