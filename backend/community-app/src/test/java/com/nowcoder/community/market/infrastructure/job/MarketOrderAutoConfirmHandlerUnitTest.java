package com.nowcoder.community.market.infrastructure.job;

import com.nowcoder.community.market.application.MarketOrderAutoConfirmApplicationService;
import com.nowcoder.community.market.application.MarketOrderAutoConfirmApplicationService.MarketOrderAutoConfirmResult;
import com.xxl.job.core.context.XxlJobContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MarketOrderAutoConfirmHandlerUnitTest {

    @AfterEach
    void clearJobContext() {
        XxlJobContext.setXxlJobContext(null);
    }

    @Test
    void handlerShouldExposePerOrderFailuresToXxlJob() {
        MarketOrderAutoConfirmApplicationService applicationService =
                mock(MarketOrderAutoConfirmApplicationService.class);
        when(applicationService.autoConfirmDueOrders())
                .thenReturn(new MarketOrderAutoConfirmResult(2, 1, 1));
        XxlJobContext.setXxlJobContext(
                new XxlJobContext(1L, "", 2L, System.currentTimeMillis(), "", 0, 1)
        );

        new MarketOrderAutoConfirmHandler(applicationService).autoConfirm();

        assertThat(XxlJobContext.getXxlJobContext().getHandleCode())
                .isEqualTo(XxlJobContext.HANDLE_CODE_FAIL);
    }
}
