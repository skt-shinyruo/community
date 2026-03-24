package com.nowcoder.community.infra.job.handlers;

import com.nowcoder.community.search.service.SearchReindexExecutionService;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class SearchReindexHandler {

    static final String JOB_NAME = "searchReindex";

    private static final Logger log = LoggerFactory.getLogger(SearchReindexHandler.class);

    private final SearchReindexExecutionService searchReindexExecutionService;

    public SearchReindexHandler(SearchReindexExecutionService searchReindexExecutionService) {
        this.searchReindexExecutionService = searchReindexExecutionService;
    }

    @XxlJob(JOB_NAME)
    public void reindex() {
        try {
            SearchReindexExecutionService.ExecutionResult result = searchReindexExecutionService.execute();
            if (result.skipped()) {
                String message = "[search] reindex skipped jobId=" + result.jobId() + " reason=" + result.reason();
                XxlJobHelper.log(message);
                XxlJobHelper.handleSuccess(message);
                log.info(message);
                return;
            }

            String message = "[search] reindex indexed-count=" + result.indexedCount() + " jobId=" + result.jobId();
            XxlJobHelper.log(message);
            XxlJobHelper.handleSuccess(message);
            log.info(message);
        } catch (RuntimeException e) {
            String message = "[search] reindex failed: " + e;
            XxlJobHelper.log(e);
            XxlJobHelper.handleFail(message);
            log.warn(message);
        }
    }
}
