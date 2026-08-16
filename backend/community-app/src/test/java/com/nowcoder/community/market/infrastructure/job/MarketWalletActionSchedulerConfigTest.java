package com.nowcoder.community.market.infrastructure.job;

import com.nowcoder.community.market.application.MarketWalletActionProcessorApplicationService;
import com.nowcoder.community.market.application.MarketWalletActionRecoveryApplicationService;
import com.nowcoder.community.market.application.MarketWalletActionRecoveryApplicationService.MarketWalletActionRecoveryResult;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MarketWalletActionSchedulerConfigTest {

    @Test
    void processorSchedulerShouldUseConfiguredBatchSize() {
        MarketWalletActionProcessorApplicationService processor = mock(MarketWalletActionProcessorApplicationService.class);
        MarketWalletActionProcessorScheduler scheduler = new MarketWalletActionProcessorScheduler(processor, 17);

        scheduler.process();

        verify(processor).processDue(17);
    }

    @Test
    void recoverySchedulerShouldUseConfiguredBatchSize() {
        MarketWalletActionRecoveryApplicationService recovery = mock(MarketWalletActionRecoveryApplicationService.class);
        when(recovery.reconcileOnce(23)).thenReturn(new MarketWalletActionRecoveryResult(0, 0, 0));
        MarketWalletActionRecoveryScheduler scheduler = new MarketWalletActionRecoveryScheduler(recovery, 23);

        scheduler.recover();

        verify(recovery).reconcileOnce(23);
    }
}
