package com.nowcoder.community.market.infrastructure.job;

import com.nowcoder.community.common.trace.TraceJobRunner;
import com.nowcoder.community.market.application.MarketOrderAutoConfirmApplicationService;
import com.nowcoder.community.market.application.result.MarketOrderAutoConfirmResult;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class MarketOrderAutoConfirmHandler {

    static final String JOB_NAME = "marketOrderAutoConfirm";

    private static final Logger log = LoggerFactory.getLogger(MarketOrderAutoConfirmHandler.class);

    private final MarketOrderAutoConfirmApplicationService applicationService;

    public MarketOrderAutoConfirmHandler(MarketOrderAutoConfirmApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @XxlJob(JOB_NAME)
    public void autoConfirm() {
        TraceJobRunner.run(JOB_NAME, () -> {
            try {
                MarketOrderAutoConfirmResult result = applicationService.autoConfirmDueOrders();
                String message = "[market] auto-confirm completed=" + result.completedCount() + " skipped=" + result.skippedCount();
                XxlJobHelper.log(message);
                XxlJobHelper.handleSuccess(message);
                log.info(message);
            } catch (RuntimeException e) {
                String message = "[market] auto-confirm failed: " + e;
                XxlJobHelper.log(e);
                XxlJobHelper.handleFail(message);
                log.warn(message);
            }
        });
    }
}
