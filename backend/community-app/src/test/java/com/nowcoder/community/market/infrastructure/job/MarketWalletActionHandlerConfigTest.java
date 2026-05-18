package com.nowcoder.community.market.infrastructure.job;

import com.nowcoder.community.market.application.MarketWalletActionProcessorApplicationService;
import com.nowcoder.community.market.application.MarketWalletActionRecoveryApplicationService;
import com.nowcoder.community.market.application.result.MarketWalletActionRecoveryResult;
import com.xxl.job.core.context.XxlJobContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MarketWalletActionHandlerConfigTest {

    @BeforeEach
    void setUp() {
        XxlJobContext.setXxlJobContext(new XxlJobContext(1L, "", 2L, System.currentTimeMillis(), "", 0, 1));
    }

    @AfterEach
    void tearDown() {
        XxlJobContext.setXxlJobContext(null);
    }

    @Test
    void processorHandlerShouldUseConfiguredBatchSize() {
        MarketWalletActionProcessorApplicationService processor = mock(MarketWalletActionProcessorApplicationService.class);
        MarketWalletActionProcessorHandler handler = new MarketWalletActionProcessorHandler(processor, 17);

        handler.process();

        verify(processor).processDue(17);
    }

    @Test
    void recoveryHandlerShouldUseConfiguredBatchSize() {
        MarketWalletActionRecoveryApplicationService recovery = mock(MarketWalletActionRecoveryApplicationService.class);
        when(recovery.reconcileOnce(23)).thenReturn(new MarketWalletActionRecoveryResult(0, 0, 0));
        MarketWalletActionRecoveryHandler handler = new MarketWalletActionRecoveryHandler(recovery, 23);

        handler.recover();

        verify(recovery).reconcileOnce(23);
    }
}
