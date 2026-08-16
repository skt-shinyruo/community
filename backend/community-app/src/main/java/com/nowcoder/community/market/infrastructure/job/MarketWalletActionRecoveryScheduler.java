package com.nowcoder.community.market.infrastructure.job;

import com.nowcoder.community.common.trace.TraceJobRunner;
import com.nowcoder.community.market.application.MarketWalletActionRecoveryApplicationService;
import com.nowcoder.community.market.application.MarketWalletActionRecoveryApplicationService.MarketWalletActionRecoveryResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "market.scheduling", name = "enabled", matchIfMissing = true)
public class MarketWalletActionRecoveryScheduler {

    static final String JOB_NAME = "marketWalletActionRecovery";

    private static final Logger log = LoggerFactory.getLogger(MarketWalletActionRecoveryScheduler.class);

    private final MarketWalletActionRecoveryApplicationService recoveryService;
    private final int recoveryBatchSize;

    public MarketWalletActionRecoveryScheduler(
            MarketWalletActionRecoveryApplicationService recoveryService,
            @Value("${market.wallet-action.recovery-batch-size:100}") int recoveryBatchSize
    ) {
        this.recoveryService = recoveryService;
        this.recoveryBatchSize = Math.max(1, recoveryBatchSize);
    }

    @Scheduled(
            initialDelayString = "${market.wallet-action.recovery-initial-delay-ms:15000}",
            fixedDelayString = "${market.wallet-action.recovery-delay-ms:60000}"
    )
    public void recover() {
        TraceJobRunner.run(JOB_NAME, () -> {
            try {
                MarketWalletActionRecoveryResult result = recoveryService.reconcileOnce(recoveryBatchSize);
                String message = "[market-wallet-action] recoveredLeases=" + result.recoveredLeases()
                        + " reconciled=" + result.reconciledCount()
                        + " skipped=" + result.skippedCount();
                log.info(message);
            } catch (RuntimeException e) {
                log.warn("[market-wallet-action] recovery failed", e);
            }
        });
    }
}
