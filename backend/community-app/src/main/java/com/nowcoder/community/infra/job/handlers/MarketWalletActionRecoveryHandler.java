package com.nowcoder.community.infra.job.handlers;

import com.nowcoder.community.market.service.MarketWalletActionRecoveryResult;
import com.nowcoder.community.market.service.MarketWalletActionRecoveryService;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class MarketWalletActionRecoveryHandler {

    static final String JOB_NAME = "marketWalletActionRecovery";

    private static final Logger log = LoggerFactory.getLogger(MarketWalletActionRecoveryHandler.class);

    private final MarketWalletActionRecoveryService recoveryService;

    public MarketWalletActionRecoveryHandler(MarketWalletActionRecoveryService recoveryService) {
        this.recoveryService = recoveryService;
    }

    @XxlJob(JOB_NAME)
    public void recover() {
        try {
            MarketWalletActionRecoveryResult result = recoveryService.reconcileOnce(100);
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
    }
}
