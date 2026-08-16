package com.nowcoder.community.market.infrastructure.job;

import com.nowcoder.community.common.trace.TraceJobRunner;
import com.nowcoder.community.market.application.MarketOrderAutoConfirmApplicationService;
import com.nowcoder.community.market.application.MarketOrderAutoConfirmApplicationService.MarketOrderAutoConfirmResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "market.scheduling", name = "enabled", matchIfMissing = true)
public class MarketOrderAutoConfirmScheduler {

    static final String JOB_NAME = "marketOrderAutoConfirm";

    private static final Logger log = LoggerFactory.getLogger(MarketOrderAutoConfirmScheduler.class);

    private final MarketOrderAutoConfirmApplicationService applicationService;

    public MarketOrderAutoConfirmScheduler(MarketOrderAutoConfirmApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @Scheduled(
            initialDelayString = "${market.order.auto-confirm.initial-delay-ms:30000}",
            fixedDelayString = "${market.order.auto-confirm.delay-ms:60000}"
    )
    public void autoConfirm() {
        TraceJobRunner.run(JOB_NAME, () -> {
            try {
                MarketOrderAutoConfirmResult result = applicationService.autoConfirmDueOrders();
                String message = "[market] auto-confirm completed=" + result.completedCount()
                        + " skipped=" + result.skippedCount()
                        + " failed=" + result.failedCount();
                if (result.failedCount() > 0) {
                    log.warn(message);
                } else {
                    log.info(message);
                }
            } catch (RuntimeException e) {
                log.warn("[market] auto-confirm failed", e);
            }
        });
    }
}
