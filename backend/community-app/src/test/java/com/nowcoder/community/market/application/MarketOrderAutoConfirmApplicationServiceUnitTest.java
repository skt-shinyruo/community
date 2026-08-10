package com.nowcoder.community.market.application;

import com.nowcoder.community.market.application.MarketOrderAutoConfirmApplicationService.MarketOrderAutoConfirmResult;
import com.nowcoder.community.market.domain.model.MarketOrder;
import com.nowcoder.community.market.domain.repository.MarketOrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static com.nowcoder.community.market.support.MarketOrderTestFixture.order;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarketOrderAutoConfirmApplicationServiceUnitTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-05-18T00:00:00Z"), ZoneOffset.UTC);

    @Mock
    private MarketOrderRepository marketOrderRepository;

    @Mock
    private MarketOrderAutoConfirmer autoConfirmer;

    @Mock
    private MarketOrderAutoConfirmRetryScheduler retryScheduler;

    @Test
    void autoConfirmDueOrdersShouldDelegateEachDueOrderToSingleOrderService() {
        UUID completedOrderId = uuid(1);
        UUID skippedOrderId = uuid(2);
        when(marketOrderRepository.findDueForAutoConfirm(any(Date.class), eq(100)))
                .thenReturn(List.of(marketOrder(completedOrderId), marketOrder(skippedOrderId)));
        when(autoConfirmer.confirmOneDueOrder(eq(completedOrderId), any(Date.class))).thenReturn(true);
        when(autoConfirmer.confirmOneDueOrder(eq(skippedOrderId), any(Date.class))).thenReturn(false);

        MarketOrderAutoConfirmResult result = new MarketOrderAutoConfirmApplicationService(
                marketOrderRepository,
                autoConfirmer,
                retryScheduler,
                CLOCK,
                100
        ).autoConfirmDueOrders();

        assertThat(result.completedCount()).isEqualTo(1);
        assertThat(result.skippedCount()).isEqualTo(1);
        assertThat(result.failedCount()).isZero();
        ArgumentCaptor<Date> now = ArgumentCaptor.forClass(Date.class);
        verify(autoConfirmer).confirmOneDueOrder(eq(completedOrderId), now.capture());
        verify(autoConfirmer).confirmOneDueOrder(eq(skippedOrderId), eq(now.getValue()));
        assertThat(now.getValue()).isEqualTo(Date.from(CLOCK.instant()));
        ArgumentCaptor<Date> nextAttemptAt = ArgumentCaptor.forClass(Date.class);
        verify(retryScheduler).defer(eq(skippedOrderId), eq(now.getValue()), nextAttemptAt.capture());
        assertThat(nextAttemptAt.getValue().getTime() - now.getValue().getTime()).isEqualTo(60_000L);
    }

    @Test
    void autoConfirmDueOrdersShouldRespectConfiguredBatchSize() {
        when(marketOrderRepository.findDueForAutoConfirm(any(Date.class), eq(1)))
                .thenReturn(List.of(marketOrder(uuid(3))));
        when(autoConfirmer.confirmOneDueOrder(eq(uuid(3)), any(Date.class))).thenReturn(true);

        MarketOrderAutoConfirmResult result = new MarketOrderAutoConfirmApplicationService(
                marketOrderRepository,
                autoConfirmer,
                retryScheduler,
                CLOCK,
                1
        ).autoConfirmDueOrders();

        assertThat(result.completedCount()).isEqualTo(1);
        verify(marketOrderRepository).findDueForAutoConfirm(any(Date.class), eq(1));
    }

    @Test
    void autoConfirmDueOrdersShouldDeferFailedRowsSoTheyDoNotStarveLaterOrders() {
        UUID failedOrderId = uuid(4);
        when(marketOrderRepository.findDueForAutoConfirm(any(Date.class), eq(100)))
                .thenReturn(List.of(marketOrder(failedOrderId)));
        when(autoConfirmer.confirmOneDueOrder(eq(failedOrderId), any(Date.class)))
                .thenThrow(new IllegalStateException("poisoned row"));

        MarketOrderAutoConfirmResult result = new MarketOrderAutoConfirmApplicationService(
                marketOrderRepository,
                autoConfirmer,
                retryScheduler,
                CLOCK,
                100
        ).autoConfirmDueOrders();

        assertThat(result.completedCount()).isZero();
        assertThat(result.skippedCount()).isEqualTo(1);
        assertThat(result.failedCount()).isEqualTo(1);
        verify(retryScheduler).defer(eq(failedOrderId), any(Date.class), any(Date.class));
    }

    @Test
    void autoConfirmDueOrdersShouldContinueWhenDeferringOneRowFails() {
        UUID deferredOrderId = uuid(5);
        UUID completedOrderId = uuid(6);
        when(marketOrderRepository.findDueForAutoConfirm(any(Date.class), eq(100)))
                .thenReturn(List.of(marketOrder(deferredOrderId), marketOrder(completedOrderId)));
        when(autoConfirmer.confirmOneDueOrder(eq(deferredOrderId), any(Date.class))).thenReturn(false);
        when(autoConfirmer.confirmOneDueOrder(eq(completedOrderId), any(Date.class))).thenReturn(true);
        doThrow(new IllegalStateException("retry store unavailable"))
                .when(retryScheduler).defer(eq(deferredOrderId), any(Date.class), any(Date.class));

        MarketOrderAutoConfirmResult result = new MarketOrderAutoConfirmApplicationService(
                marketOrderRepository,
                autoConfirmer,
                retryScheduler,
                CLOCK,
                100
        ).autoConfirmDueOrders();

        assertThat(result.completedCount()).isEqualTo(1);
        assertThat(result.skippedCount()).isEqualTo(1);
        assertThat(result.failedCount()).isEqualTo(1);
        verify(autoConfirmer).confirmOneDueOrder(eq(completedOrderId), any(Date.class));
    }

    private MarketOrder marketOrder(UUID orderId) {
        return order(orderId).build();
    }
}
