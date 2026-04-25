package com.nowcoder.community.market.service;

import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class MarketOrderSagaService {

    public boolean canApplyEscrow(UUID orderId) {
        return true;
    }

    public void completeEscrowNoop(UUID orderId) {
    }

    public boolean markEscrowSucceeded(UUID orderId, UUID escrowTxnId) {
        return false;
    }

    public void markEscrowTerminalFailed(UUID orderId, String reason) {
    }

    public boolean markReleaseSucceeded(UUID orderId, UUID releaseTxnId) {
        return false;
    }

    public boolean markRefundSucceeded(UUID orderId, UUID refundTxnId) {
        return false;
    }
}
