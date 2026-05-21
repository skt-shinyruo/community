package com.nowcoder.community.market.application;

import com.nowcoder.community.market.domain.model.MarketListing;
import com.nowcoder.community.market.domain.model.MarketOrder;
import com.nowcoder.community.market.domain.repository.MarketInventoryRepository;
import com.nowcoder.community.market.domain.repository.MarketListingRepository;
import com.nowcoder.community.market.domain.repository.MarketOrderRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;

@Service
public class MarketOrderSagaApplicationService {

    private final MarketOrderRepository marketOrderRepository;
    private final MarketListingRepository marketListingRepository;
    private final MarketInventoryRepository marketInventoryRepository;

    @Autowired
    public MarketOrderSagaApplicationService(MarketOrderRepository marketOrderRepository,
                                  MarketListingRepository marketListingRepository,
                                  MarketInventoryRepository marketInventoryRepository) {
        this.marketOrderRepository = marketOrderRepository;
        this.marketListingRepository = marketListingRepository;
        this.marketInventoryRepository = marketInventoryRepository;
    }

    @Transactional(readOnly = true)
    public boolean canApplyEscrow(UUID orderId) {
        MarketOrder order = marketOrderRepository.findById(orderId);
        return order != null && order.isEscrowPending();
    }

    @Transactional
    public void completeEscrowNoop(UUID orderId) {
        MarketOrder order = marketOrderRepository.lockById(orderId);
        int updated = marketOrderRepository.markCancelledNoRefund(orderId);
        if (updated == 1) {
            restoreMarketSideCompensation(order);
        }
    }

    @Transactional
    public boolean markEscrowSucceeded(UUID orderId, UUID escrowTxnId) {
        int updated = marketOrderRepository.markEscrowSucceeded(orderId, escrowTxnId);
        if (updated != 1) {
            return false;
        }
        MarketOrder order = marketOrderRepository.lockById(orderId);
        deliverPreloadedInventoryIfNeeded(order);
        return true;
    }

    @Transactional
    public boolean markEscrowCancelRefundPending(UUID orderId, UUID escrowTxnId) {
        return marketOrderRepository.markEscrowCancelRefundPending(orderId, escrowTxnId) == 1;
    }

    @Transactional
    public void markEscrowTerminalFailed(UUID orderId, String reason) {
        MarketOrder order = marketOrderRepository.lockById(orderId);
        if (order == null) {
            return;
        }
        int updated = marketOrderRepository.markEscrowFailed(orderId);
        if (updated != 1 && order.isEscrowCancelPending()) {
            updated = marketOrderRepository.markCancelledNoRefund(orderId);
        }
        if (updated == 1) {
            restoreMarketSideCompensation(order);
        }
    }

    @Transactional
    public boolean markReleaseSucceeded(UUID orderId, UUID releaseTxnId) {
        return marketOrderRepository.markReleaseSucceeded(orderId, releaseTxnId) == 1;
    }

    @Transactional
    public boolean markRefundSucceeded(UUID orderId, UUID refundTxnId) {
        MarketOrder order = marketOrderRepository.lockById(orderId);
        if (order == null) {
            return false;
        }
        int updated = marketOrderRepository.markCancelledWithRefund(orderId, refundTxnId);
        if (updated != 1) {
            updated = marketOrderRepository.markDisputeRefundSucceeded(orderId, refundTxnId);
        }
        if (updated == 1) {
            restoreMarketSideCompensation(order);
            return true;
        }
        return false;
    }

    private void deliverPreloadedInventoryIfNeeded(MarketOrder order) {
        if (order == null || !order.isPreloadedDelivery()) {
            return;
        }
        Date deliveredAt = new Date();
        marketInventoryRepository.markDeliveredByOrderIfReserved(order.getOrderId(), deliveredAt);
        marketOrderRepository.markDelivered(order.getOrderId(), Date.from(Instant.now().plus(24, ChronoUnit.HOURS)));
    }

    private void restoreMarketSideCompensation(MarketOrder order) {
        if (order == null) {
            return;
        }
        MarketListing listing = marketListingRepository.lockById(order.getListingId());
        if (listing != null && listing.isFiniteStock()) {
            marketListingRepository.adjustStock(
                    listing.getListingId(),
                    listing.getSellerUserId(),
                    0,
                    order.getQuantity(),
                    listing.statusAfterStockRestoredBy(order.getQuantity())
            );
        }
        if (order.isPreloadedDelivery()) {
            marketInventoryRepository.releaseReservedByOrderIfNeeded(order.getOrderId());
        }
    }
}
