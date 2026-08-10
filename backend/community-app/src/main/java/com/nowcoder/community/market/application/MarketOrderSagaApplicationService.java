package com.nowcoder.community.market.application;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.market.domain.model.MarketListing;
import com.nowcoder.community.market.domain.model.MarketOrder;
import com.nowcoder.community.market.domain.model.MarketOrderStatus;
import com.nowcoder.community.market.domain.repository.MarketInventoryRepository;
import com.nowcoder.community.market.domain.repository.MarketListingRepository;
import com.nowcoder.community.market.domain.repository.MarketOrderRepository;
import com.nowcoder.community.market.exception.MarketErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Objects;
import java.util.UUID;

@Service
public class MarketOrderSagaApplicationService {

    private static final Logger log = LoggerFactory.getLogger(MarketOrderSagaApplicationService.class);

    private final MarketOrderRepository marketOrderRepository;
    private final MarketListingRepository marketListingRepository;
    private final MarketInventoryRepository marketInventoryRepository;
    private final Clock clock;

    @Autowired
    public MarketOrderSagaApplicationService(MarketOrderRepository marketOrderRepository,
                                             MarketListingRepository marketListingRepository,
                                             MarketInventoryRepository marketInventoryRepository,
                                             Clock clock) {
        this.marketOrderRepository = Objects.requireNonNull(marketOrderRepository, "marketOrderRepository must not be null");
        this.marketListingRepository = Objects.requireNonNull(marketListingRepository, "marketListingRepository must not be null");
        this.marketInventoryRepository = Objects.requireNonNull(marketInventoryRepository, "marketInventoryRepository must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Transactional(readOnly = true)
    public boolean canApplyEscrow(UUID orderId) {
        MarketOrder order = marketOrderRepository.findById(orderId);
        return order != null && order.isEscrowPending();
    }

    @Transactional
    public void completeEscrowNoop(UUID orderId) {
        MarketOrder order = marketOrderRepository.lockById(orderId);
        if (order == null
                || (order.status() != MarketOrderStatus.ESCROW_CANCEL_PENDING
                && order.status() != MarketOrderStatus.ESCROW_FAILED)) {
            return;
        }
        MarketOrderStatus fromStatus = order.status();
        boolean restoreInventory = order.holdsReservedInventoryForEscrowCancellation();
        MarketOrderRepository.ApplyStatus outcome = marketOrderRepository.apply(order.cancelWithoutRefund());
        boolean transitionAffected = outcome == MarketOrderRepository.ApplyStatus.APPLIED;
        boolean inventoryCompensationAttempted = transitionAffected && restoreInventory;
        log.info(
                "[market-escrow-noop] orderId={} fromStatus={} transitionAffected={} inventoryCompensationAttempted={}",
                orderId,
                fromStatus,
                transitionAffected,
                inventoryCompensationAttempted
        );
        if (inventoryCompensationAttempted) {
            restoreReservedInventoryAndStock(order);
        }
    }

    @Transactional
    public boolean markEscrowSucceeded(UUID orderId, UUID escrowTxnId) {
        MarketOrder order = marketOrderRepository.lockById(orderId);
        if (order == null || order.status() != MarketOrderStatus.ESCROW_PENDING) {
            return false;
        }
        if (marketOrderRepository.apply(order.recordEscrowSucceeded(escrowTxnId))
                != MarketOrderRepository.ApplyStatus.APPLIED) {
            return false;
        }
        MarketOrder escrowed = marketOrderRepository.lockById(orderId);
        if (escrowed == null) {
            throw new IllegalStateException("market order disappeared after escrow transition: orderId=" + orderId);
        }
        deliverPreloadedInventoryIfNeeded(escrowed);
        return true;
    }

    @Transactional
    public boolean markEscrowCancelRefundPending(UUID orderId, UUID escrowTxnId) {
        MarketOrder order = marketOrderRepository.lockById(orderId);
        if (order == null || order.status() != MarketOrderStatus.ESCROW_CANCEL_PENDING) {
            return false;
        }
        return marketOrderRepository.apply(order.recordLateEscrowSucceeded(escrowTxnId))
                == MarketOrderRepository.ApplyStatus.APPLIED;
    }

    @Transactional
    public void markEscrowTerminalFailed(UUID orderId, String reason) {
        MarketOrder order = marketOrderRepository.lockById(orderId);
        if (order == null) {
            return;
        }

        MarketOrderRepository.ApplyStatus outcome;
        if (order.status() == MarketOrderStatus.ESCROW_PENDING) {
            outcome = marketOrderRepository.apply(order.recordEscrowFailed());
        } else if (order.status() == MarketOrderStatus.ESCROW_CANCEL_PENDING) {
            outcome = marketOrderRepository.apply(order.cancelWithoutRefund());
        } else {
            return;
        }
        if (outcome == MarketOrderRepository.ApplyStatus.APPLIED) {
            restoreReservedInventoryAndStock(order);
        }
    }

    @Transactional
    public boolean markReleaseSucceeded(UUID orderId, UUID releaseTxnId) {
        MarketOrder order = marketOrderRepository.lockById(orderId);
        if (order == null
                || (order.status() != MarketOrderStatus.RELEASE_PENDING
                && order.status() != MarketOrderStatus.DISPUTE_RELEASE_PENDING)) {
            return false;
        }
        return marketOrderRepository.apply(order.recordReleaseSucceeded(releaseTxnId))
                == MarketOrderRepository.ApplyStatus.APPLIED;
    }

    @Transactional
    public boolean markRefundSucceeded(UUID orderId, UUID refundTxnId) {
        MarketOrder order = marketOrderRepository.lockById(orderId);
        if (order == null
                || (order.status() != MarketOrderStatus.REFUND_PENDING
                && order.status() != MarketOrderStatus.DISPUTE_REFUND_PENDING)) {
            return false;
        }
        boolean restoreInventory = order.status() == MarketOrderStatus.REFUND_PENDING;
        if (marketOrderRepository.apply(order.recordRefundSucceeded(refundTxnId))
                != MarketOrderRepository.ApplyStatus.APPLIED) {
            return false;
        }
        if (restoreInventory) {
            restoreReservedInventoryAndStock(order);
        }
        return true;
    }

    private void deliverPreloadedInventoryIfNeeded(MarketOrder order) {
        if (!order.isPreloadedDelivery()) {
            return;
        }
        Date deliveredAt = Date.from(clock.instant());
        marketInventoryRepository.markDeliveredByOrderIfReserved(order.getOrderId(), deliveredAt);
        if (marketOrderRepository.apply(order.markDelivered(
                Date.from(clock.instant().plus(24, ChronoUnit.HOURS))
        )) != MarketOrderRepository.ApplyStatus.APPLIED) {
            throw new IllegalStateException(
                    "preloaded market order delivery transition was stale: orderId=" + order.getOrderId()
            );
        }
    }

    private void restoreReservedInventoryAndStock(MarketOrder order) {
        MarketListing listing = marketListingRepository.lockById(order.getListingId());
        if (listing != null && listing.isFiniteStock()) {
            int updated = marketListingRepository.adjustStock(
                    listing.getListingId(),
                    listing.getSellerUserId(),
                    0,
                    order.getQuantity(),
                    listing.getStatus(),
                    listing.statusAfterStockRestoredBy(order.getQuantity())
            );
            if (updated != 1) {
                throw new BusinessException(
                        MarketErrorCode.LISTING_TRANSITION_CONFLICT,
                        "market listing stock transition conflict: listingId=" + listing.getListingId()
                );
            }
        }
        if (order.isPreloadedDelivery()) {
            marketInventoryRepository.releaseReservedByOrderIfNeeded(order.getOrderId());
        }
    }
}
