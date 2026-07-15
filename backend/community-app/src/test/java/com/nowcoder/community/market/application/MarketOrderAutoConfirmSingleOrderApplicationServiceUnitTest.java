package com.nowcoder.community.market.application;

import com.nowcoder.community.market.domain.model.MarketOrder;
import com.nowcoder.community.market.domain.model.MarketOrderStatus;
import com.nowcoder.community.market.domain.model.MarketOrderTransition;
import com.nowcoder.community.market.domain.repository.MarketOrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Date;
import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static com.nowcoder.community.market.support.MarketOrderTestFixture.order;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarketOrderAutoConfirmSingleOrderApplicationServiceUnitTest {

    @Mock
    private MarketOrderRepository marketOrderRepository;

    @Mock
    private MarketWalletActionApplicationService marketWalletActionService;

    @Test
    void confirmOneDueOrderShouldLockOrderMarkReleasePendingAndEnqueueRelease() {
        UUID orderId = uuid(1);
        UUID sellerUserId = uuid(2);
        UUID buyerUserId = uuid(3);
        Date now = new Date();
        MarketOrder order = order(orderId, sellerUserId, buyerUserId, "DELIVERED", new Date(now.getTime() - 1_000L));
        when(marketOrderRepository.lockById(orderId)).thenReturn(order);
        when(marketOrderRepository.apply(any(MarketOrderTransition.class)))
                .thenReturn(MarketOrderRepository.ApplyStatus.APPLIED);

        boolean confirmed = new MarketOrderAutoConfirmSingleOrderApplicationService(
                marketOrderRepository,
                marketWalletActionService
        ).confirmOneDueOrder(orderId, now);

        assertThat(confirmed).isTrue();
        verify(marketOrderRepository).lockById(orderId);
        ArgumentCaptor<MarketOrderTransition> transition = ArgumentCaptor.forClass(MarketOrderTransition.class);
        verify(marketOrderRepository).apply(transition.capture());
        assertThat(transition.getValue().orderId()).isEqualTo(orderId);
        assertThat(transition.getValue().nextStatus()).isEqualTo(MarketOrderStatus.RELEASE_PENDING);
        verify(marketWalletActionService).enqueueRelease(orderId, sellerUserId, buyerUserId, 1200L);
    }

    @Test
    void confirmOneDueOrderShouldSkipOrdersThatAreNoLongerDue() {
        UUID orderId = uuid(1);
        Date now = new Date();
        MarketOrder order = order(orderId, uuid(2), uuid(3), "COMPLETED", new Date(now.getTime() - 1_000L));
        when(marketOrderRepository.lockById(orderId)).thenReturn(order);

        boolean confirmed = new MarketOrderAutoConfirmSingleOrderApplicationService(
                marketOrderRepository,
                marketWalletActionService
        ).confirmOneDueOrder(orderId, now);

        assertThat(confirmed).isFalse();
        verify(marketOrderRepository, never()).apply(any(MarketOrderTransition.class));
        verify(marketWalletActionService, never()).enqueueRelease(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyLong()
        );
    }

    @Test
    void confirmOneDueOrderShouldSkipWhenAutoConfirmTimeIsMissing() {
        UUID orderId = uuid(1);
        Date now = new Date();
        MarketOrder order = order(orderId, uuid(2), uuid(3), "DELIVERED", null);
        when(marketOrderRepository.lockById(orderId)).thenReturn(order);

        boolean confirmed = new MarketOrderAutoConfirmSingleOrderApplicationService(
                marketOrderRepository,
                marketWalletActionService
        ).confirmOneDueOrder(orderId, now);

        assertThat(confirmed).isFalse();
        verify(marketOrderRepository, never()).apply(any(MarketOrderTransition.class));
        verify(marketWalletActionService, never()).enqueueRelease(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyLong()
        );
    }

    private MarketOrder order(UUID orderId, UUID sellerUserId, UUID buyerUserId, String status, Date autoConfirmAt) {
        return com.nowcoder.community.market.support.MarketOrderTestFixture.order(orderId)
                .sellerUserId(sellerUserId)
                .buyerUserId(buyerUserId)
                .status(status)
                .autoConfirmAt(autoConfirmAt)
                .totalAmount(1_200L)
                .build();
    }
}
