package com.nowcoder.community.infra.job.handlers;

import com.nowcoder.community.market.service.MarketWalletActionProcessor;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class MarketWalletActionProcessorHandler {

    static final String JOB_NAME = "marketWalletActionProcessor";

    private static final Logger log = LoggerFactory.getLogger(MarketWalletActionProcessorHandler.class);

    private final MarketWalletActionProcessor processor;

    public MarketWalletActionProcessorHandler(MarketWalletActionProcessor processor) {
        this.processor = processor;
    }

    @XxlJob(JOB_NAME)
    public void process() {
        try {
            int processed = processor.processDue(50);
            String message = "[market-wallet-action] processed=" + processed;
            XxlJobHelper.log(message);
            XxlJobHelper.handleSuccess(message);
            log.info(message);
        } catch (RuntimeException e) {
            String message = "[market-wallet-action] process failed: " + e;
            XxlJobHelper.log(e);
            XxlJobHelper.handleFail(message);
            log.warn(message);
        }
    }
}
