package com.nowcoder.community.market.service;

import com.nowcoder.community.common.id.UuidV7Generator;
import com.nowcoder.community.market.entity.MarketWalletAction;
import com.nowcoder.community.market.mapper.MarketWalletActionMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MarketWalletActionServiceTest {

    @Test
    void enqueueEscrowShouldUseOrderIdBasedRequestId() {
        MarketWalletActionMapper mapper = mock(MarketWalletActionMapper.class);
        UuidV7Generator idGenerator = mock(UuidV7Generator.class);
        UUID actionId = uuid(1);
        UUID orderId = uuid(2);
        when(idGenerator.next()).thenReturn(actionId);

        MarketWalletActionService service = new MarketWalletActionService(mapper, idGenerator);
        service.enqueueEscrow(orderId, uuid(9), uuid(7), 12_900L);

        ArgumentCaptor<MarketWalletAction> captor = ArgumentCaptor.forClass(MarketWalletAction.class);
        verify(mapper).insert(captor.capture());
        assertThat(captor.getValue().getActionId()).isEqualTo(actionId);
        assertThat(captor.getValue().getOrderId()).isEqualTo(orderId);
        assertThat(captor.getValue().getRequestId()).isEqualTo("market-order:" + orderId + ":escrow");
        assertThat(captor.getValue().getWalletBizId()).isEqualTo("market-order:" + orderId);
        assertThat(captor.getValue().getStatus()).isEqualTo("PENDING");
        assertThat(captor.getValue().getActionType()).isEqualTo("ESCROW");
    }
}
