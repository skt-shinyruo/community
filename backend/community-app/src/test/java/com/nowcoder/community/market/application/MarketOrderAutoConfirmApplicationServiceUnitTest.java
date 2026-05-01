package com.nowcoder.community.market.application;

import com.nowcoder.community.market.api.model.MarketOrderAutoConfirmResult;
import com.nowcoder.community.market.domain.model.MarketOrder;
import com.nowcoder.community.market.domain.repository.MarketOrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Date;
import java.util.List;
import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarketOrderAutoConfirmApplicationServiceUnitTest {

    @Mock
    private MarketOrderRepository marketOrderRepository;

    @Mock
    private MarketOrderAutoConfirmSingleOrderApplicationService singleOrderApplicationService;

    @Test
    void autoConfirmDueOrdersShouldDelegateEachDueOrderToSingleOrderService() {
        UUID completedOrderId = uuid(1);
        UUID skippedOrderId = uuid(2);
        when(marketOrderRepository.findDueForAutoConfirm(any(Date.class)))
                .thenReturn(List.of(order(completedOrderId), order(skippedOrderId)));
        when(singleOrderApplicationService.confirmOneDueOrder(eq(completedOrderId), any(Date.class))).thenReturn(true);
        when(singleOrderApplicationService.confirmOneDueOrder(eq(skippedOrderId), any(Date.class))).thenReturn(false);

        MarketOrderAutoConfirmResult result = new MarketOrderAutoConfirmApplicationService(
                marketOrderRepository,
                singleOrderApplicationService
        ).autoConfirmDueOrders();

        assertThat(result.completedCount()).isEqualTo(1);
        assertThat(result.skippedCount()).isEqualTo(1);
        ArgumentCaptor<Date> now = ArgumentCaptor.forClass(Date.class);
        verify(singleOrderApplicationService).confirmOneDueOrder(eq(completedOrderId), now.capture());
        verify(singleOrderApplicationService).confirmOneDueOrder(eq(skippedOrderId), eq(now.getValue()));
    }

    private MarketOrder order(UUID orderId) {
        MarketOrder order = new MarketOrder();
        order.setOrderId(orderId);
        return order;
    }
}
