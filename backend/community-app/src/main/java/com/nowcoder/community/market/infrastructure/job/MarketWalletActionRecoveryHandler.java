package com.nowcoder.community.market.infrastructure.job;

import com.nowcoder.community.common.trace.TraceJobRunner;
import com.nowcoder.community.market.application.MarketWalletActionRecoveryApplicationService;
import com.nowcoder.community.market.application.result.MarketWalletActionRecoveryResult;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class MarketWalletActionRecoveryHandler {

    static final String JOB_NAME = "marketWalletActionRecovery";

    private static final Logger log = LoggerFactory.getLogger(MarketWalletActionRecoveryHandler.class);

    private final MarketWalletActionRecoveryApplicationService recoveryService;
    private final int recoveryBatchSize;

    public MarketWalletActionRecoveryHandler(
            MarketWalletActionRecoveryApplicationService recoveryService,
            @Value("${market.wallet-action.recovery-batch-size:100}") int recoveryBatchSize
    ) {
        this.recoveryService = recoveryService;
        this.recoveryBatchSize = Math.max(1, recoveryBatchSize);
    }

    @XxlJob(JOB_NAME)
    public void recover() {
        TraceJobRunner.run(JOB_NAME, () -> {
            try {
                MarketWalletActionRecoveryResult result = recoveryService.reconcileOnce(recoveryBatchSize);
                String message = "[market-wallet-action] recoveredLeases=" + result.recoveredLeases()
                        + " reconciled=" + result.reconciledCount()
                        + " skipped=" + result.skippedCount();
                XxlJobHelper.log(message);
                XxlJobHelper.handleSuccess(message);
                log.info(message);
            } catch (RuntimeException e) {
                String message = "[market-wallet-action] recovery failed: " + e;
                XxlJobHelper.log(e);
                XxlJobHelper.handleFail(message);
                log.warn(message);
            }
        });
    }
}
