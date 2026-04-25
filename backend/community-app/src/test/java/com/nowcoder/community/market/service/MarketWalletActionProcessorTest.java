package com.nowcoder.community.market.service;

import com.nowcoder.community.market.entity.MarketWalletAction;
import com.nowcoder.community.market.mapper.MarketWalletActionMapper;
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

class MarketWalletActionProcessorTest {

    @Test
    void processOneShouldNoopEscrowWhenSagaRejectsForwardAction() {
        MarketWalletActionMapper mapper = mock(MarketWalletActionMapper.class);
        MarketOrderSagaService sagaService = mock(MarketOrderSagaService.class);
        WalletMarketActionApi walletApi = mock(WalletMarketActionApi.class);
        MarketWalletAction action = escrowAction();
        when(mapper.claimProcessing(eq(action.getActionId()), any())).thenReturn(1);
        when(sagaService.canApplyEscrow(action.getOrderId())).thenReturn(false);

        MarketWalletActionProcessor processor = new MarketWalletActionProcessor(mapper, walletApi, sagaService, Clock.systemUTC());

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
