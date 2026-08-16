package com.nowcoder.community.market.infrastructure.job;

import com.nowcoder.community.common.trace.TraceJobRunner;
import com.nowcoder.community.market.application.MarketWalletActionProcessorApplicationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "market.scheduling", name = "enabled", matchIfMissing = true)
public class MarketWalletActionProcessorScheduler {

    static final String JOB_NAME = "marketWalletActionProcessor";

    private static final Logger log = LoggerFactory.getLogger(MarketWalletActionProcessorScheduler.class);

    private final MarketWalletActionProcessorApplicationService processor;
    private final int processBatchSize;

    public MarketWalletActionProcessorScheduler(
            MarketWalletActionProcessorApplicationService processor,
            @Value("${market.wallet-action.process-batch-size:50}") int processBatchSize
    ) {
        this.processor = processor;
        this.processBatchSize = Math.max(1, processBatchSize);
    }

    @Scheduled(
            initialDelayString = "${market.wallet-action.process-initial-delay-ms:5000}",
            fixedDelayString = "${market.wallet-action.process-delay-ms:5000}"
    )
    public void process() {
        TraceJobRunner.run(JOB_NAME, () -> {
            try {
                int processed = processor.processDue(processBatchSize);
                String message = "[market-wallet-action] processed=" + processed;
                log.info(message);
            } catch (RuntimeException e) {
                log.warn("[market-wallet-action] process failed", e);
            }
        });
    }
}
