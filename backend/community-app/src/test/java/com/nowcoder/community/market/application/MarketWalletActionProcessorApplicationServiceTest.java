package com.nowcoder.community.market.application;

import com.nowcoder.community.market.domain.model.MarketWalletAction;
import com.nowcoder.community.market.infrastructure.persistence.MyBatisMarketWalletActionRepository;
import com.nowcoder.community.market.infrastructure.persistence.mapper.MarketWalletActionMapper;
import com.nowcoder.community.wallet.api.action.WalletMarketActionApi;
import org.junit.jupiter.api.Test;

import java.time.Clock;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MarketWalletActionProcessorApplicationServiceTest {

    @Test
    void processOneShouldNoopEscrowWhenSagaRejectsForwardAction() {
        MarketWalletActionMapper mapper = mock(MarketWalletActionMapper.class);
        MarketOrderSagaApplicationService sagaService = mock(MarketOrderSagaApplicationService.class);
        MarketWalletActionApplicationService actionService = mock(MarketWalletActionApplicationService.class);
        WalletMarketActionApi walletApi = mock(WalletMarketActionApi.class);
        MarketWalletAction action = escrowAction();
        when(mapper.claimProcessing(eq(action.getActionId()), any())).thenReturn(1);
        when(sagaService.canApplyEscrow(action.getOrderId())).thenReturn(false);

        MarketWalletActionProcessorApplicationService processor = new MarketWalletActionProcessorApplicationService(
                new MyBatisMarketWalletActionRepository(mapper),
                walletApi,
                sagaService,
                actionService,
                Clock.systemUTC()
        );

        processor.processOne(action);

        verify(walletApi, never()).escrowOrder(any(), any(), anyLong(), any());
        verify(mapper).markCancelled(action.getActionId(), "NOOP");
    }

    private MarketWalletAction escrowAction() {
        MarketWalletAction action = new MarketWalletAction();
        action.setActionId(uuid(11));
        action.setOrderId(uuid(22));
        action.setActionType("ESCROW");
        action.setRequestId("market-order:" + action.getOrderId() + ":escrow");
        action.setWalletBizId("market-order:" + action.getOrderId());
        action.setActorUserId(uuid(9));
        action.setCounterpartyUserId(uuid(7));
        action.setAmount(12_900L);
        action.setStatus("PENDING");
        return action;
    }
}
