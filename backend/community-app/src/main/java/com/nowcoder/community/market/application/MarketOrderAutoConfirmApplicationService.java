package com.nowcoder.community.market.application;

import com.nowcoder.community.market.domain.model.MarketOrder;
import com.nowcoder.community.market.domain.repository.MarketOrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Date;

@Service
public class MarketOrderAutoConfirmApplicationService {

    private static final Logger log = LoggerFactory.getLogger(MarketOrderAutoConfirmApplicationService.class);
    private static final long RETRY_DELAY_MILLIS = 60_000L;

    private final MarketOrderRepository marketOrderRepository;
    private final MarketOrderAutoConfirmer autoConfirmer;
    private final MarketOrderAutoConfirmRetryScheduler retryScheduler;
    private final int batchSize;

    public MarketOrderAutoConfirmApplicationService(
            MarketOrderRepository marketOrderRepository,
            MarketOrderAutoConfirmer autoConfirmer,
            MarketOrderAutoConfirmRetryScheduler retryScheduler,
            @Value("${market.order.auto-confirm-batch-size:100}") int batchSize
    ) {
        this.marketOrderRepository = marketOrderRepository;
        this.autoConfirmer = autoConfirmer;
        this.retryScheduler = retryScheduler;
        this.batchSize = Math.min(1_000, Math.max(1, batchSize));
    }

    public MarketOrderAutoConfirmResult autoConfirmDueOrders() {
        int completed = 0;
        int skipped = 0;
        int failed = 0;
        Date now = new Date();
        for (MarketOrder dueOrder : marketOrderRepository.findDueForAutoConfirm(now, batchSize)) {
            boolean confirmed = false;
            boolean rowFailed = false;
            try {
                confirmed = autoConfirmer.confirmOneDueOrder(dueOrder.getOrderId(), now);
                if (confirmed) {
                    completed++;
                } else {
                    skipped++;
                }
            } catch (RuntimeException e) {
                skipped++;
                failed++;
                rowFailed = true;
                log.warn("market auto-confirm failed; order deferred: orderId={}", dueOrder.getOrderId(), e);
            }
            if (!confirmed) {
                try {
                    retryScheduler.defer(
                            dueOrder.getOrderId(),
                            now,
                            new Date(now.getTime() + RETRY_DELAY_MILLIS)
                    );
                } catch (RuntimeException deferFailure) {
                    if (!rowFailed) {
                        failed++;
                    }
                    log.warn("market auto-confirm defer failed; continuing batch: orderId={}",
                            dueOrder.getOrderId(), deferFailure);
                }
            }
        }
        return new MarketOrderAutoConfirmResult(completed, skipped, failed);
    }

    public record MarketOrderAutoConfirmResult(
            int completedCount,
            int skippedCount,
            int failedCount
    ) {
    }
}
