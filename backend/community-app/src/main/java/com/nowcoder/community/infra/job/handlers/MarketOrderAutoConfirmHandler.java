package com.nowcoder.community.infra.job.handlers;

import com.nowcoder.community.market.api.action.MarketOrderAutoConfirmActionApi;
import com.nowcoder.community.market.api.model.MarketOrderAutoConfirmResult;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class MarketOrderAutoConfirmHandler {

    static final String JOB_NAME = "marketOrderAutoConfirm";

    private static final Logger log = LoggerFactory.getLogger(MarketOrderAutoConfirmHandler.class);

    private final MarketOrderAutoConfirmActionApi marketOrderAutoConfirmActionApi;

    public MarketOrderAutoConfirmHandler(MarketOrderAutoConfirmActionApi marketOrderAutoConfirmActionApi) {
        this.marketOrderAutoConfirmActionApi = marketOrderAutoConfirmActionApi;
    }

    @XxlJob(JOB_NAME)
    public void autoConfirm() {
        try {
            MarketOrderAutoConfirmResult result = marketOrderAutoConfirmActionApi.autoConfirmDueOrders();
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
    }
}
