package com.nowcoder.community.market.infrastructure.job;

import com.nowcoder.community.common.trace.TraceJobRunner;
import com.nowcoder.community.market.application.MarketWalletActionProcessorApplicationService;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class MarketWalletActionProcessorHandler {

    static final String JOB_NAME = "marketWalletActionProcessor";

    private static final Logger log = LoggerFactory.getLogger(MarketWalletActionProcessorHandler.class);

    private final MarketWalletActionProcessorApplicationService processor;
    private final int processBatchSize;

    public MarketWalletActionProcessorHandler(
            MarketWalletActionProcessorApplicationService processor,
            @Value("${market.wallet-action.process-batch-size:50}") int processBatchSize
    ) {
        this.processor = processor;
        this.processBatchSize = Math.max(1, processBatchSize);
    }

    @XxlJob(JOB_NAME)
    public void process() {
        TraceJobRunner.run(JOB_NAME, () -> {
            try {
                int processed = processor.processDue(processBatchSize);
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
        });
    }
}
