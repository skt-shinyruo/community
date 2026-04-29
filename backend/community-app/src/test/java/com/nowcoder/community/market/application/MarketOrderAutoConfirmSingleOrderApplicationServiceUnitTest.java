package com.nowcoder.community.market.application;

import com.nowcoder.community.market.domain.model.MarketOrder;
import com.nowcoder.community.market.domain.repository.MarketOrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Date;
import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
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
        order.setTotalAmount(1200L);
        when(marketOrderRepository.selectByIdForUpdate(orderId)).thenReturn(order);
        when(marketOrderRepository.markReleasePending(orderId)).thenReturn(1);

        boolean confirmed = new MarketOrderAutoConfirmSingleOrderApplicationService(
                marketOrderRepository,
                marketWalletActionService
        ).confirmOneDueOrder(orderId, now);

        assertThat(confirmed).isTrue();
        verify(marketOrderRepository).selectByIdForUpdate(orderId);
        verify(marketOrderRepository).markReleasePending(orderId);
        verify(marketWalletActionService).enqueueRelease(orderId, sellerUserId, buyerUserId, 1200L);
    }

    @Test
    void confirmOneDueOrderShouldSkipOrdersThatAreNoLongerDue() {
        UUID orderId = uuid(1);
        Date now = new Date();
        MarketOrder order = order(orderId, uuid(2), uuid(3), "COMPLETED", new Date(now.getTime() - 1_000L));
        when(marketOrderRepository.selectByIdForUpdate(orderId)).thenReturn(order);

        boolean confirmed = new MarketOrderAutoConfirmSingleOrderApplicationService(
                marketOrderRepository,
                marketWalletActionService
        ).confirmOneDueOrder(orderId, now);

        assertThat(confirmed).isFalse();
        verify(marketOrderRepository, never()).markReleasePending(orderId);
        verify(marketWalletActionService, never()).enqueueRelease(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyLong()
        );
    }

    private MarketOrder order(UUID orderId, UUID sellerUserId, UUID buyerUserId, String status, Date autoConfirmAt) {
        MarketOrder order = new MarketOrder();
        order.setOrderId(orderId);
        order.setSellerUserId(sellerUserId);
        order.setBuyerUserId(buyerUserId);
        order.setStatus(status);
        order.setAutoConfirmAt(autoConfirmAt);
        return order;
    }
}
