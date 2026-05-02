package com.nowcoder.community.search.infrastructure.job;

import com.nowcoder.community.search.application.SearchReindexApplicationService;
import com.nowcoder.community.search.application.command.ReindexPostsCommand;
import com.nowcoder.community.search.application.result.SearchReindexResult;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class SearchReindexHandler {

    static final String JOB_NAME = "searchReindex";

    private static final Logger log = LoggerFactory.getLogger(SearchReindexHandler.class);

    private final SearchReindexApplicationService applicationService;

    public SearchReindexHandler(SearchReindexApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @XxlJob(JOB_NAME)
    public void reindex() {
        try {
            SearchReindexResult result = applicationService.reindex(new ReindexPostsCommand());
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
