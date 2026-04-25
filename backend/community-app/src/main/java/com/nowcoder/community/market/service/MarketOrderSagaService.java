package com.nowcoder.community.market.service;

import com.nowcoder.community.market.entity.MarketListing;
import com.nowcoder.community.market.entity.MarketOrder;
import com.nowcoder.community.market.mapper.MarketInventoryUnitMapper;
import com.nowcoder.community.market.mapper.MarketListingMapper;
import com.nowcoder.community.market.mapper.MarketOrderMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;

@Service
public class MarketOrderSagaService {

    private static final String GOODS_TYPE_PHYSICAL = "PHYSICAL";
    private static final String STOCK_MODE_FINITE = "FINITE";
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_SOLD_OUT = "SOLD_OUT";
    private static final String STATUS_ESCROW_PENDING = "ESCROW_PENDING";
    private static final String STATUS_ESCROW_CANCEL_PENDING = "ESCROW_CANCEL_PENDING";
    private static final String DELIVERY_MODE_PRELOADED = "PRELOADED";

    private final MarketOrderMapper marketOrderMapper;
    private final MarketListingMapper marketListingMapper;
    private final MarketInventoryUnitMapper marketInventoryUnitMapper;

    @Autowired
    public MarketOrderSagaService(MarketOrderMapper marketOrderMapper,
                                  MarketListingMapper marketListingMapper,
                                  MarketInventoryUnitMapper marketInventoryUnitMapper) {
        this.marketOrderMapper = marketOrderMapper;
        this.marketListingMapper = marketListingMapper;
        this.marketInventoryUnitMapper = marketInventoryUnitMapper;
    }

    @Transactional(readOnly = true)
    public boolean canApplyEscrow(UUID orderId) {
        MarketOrder order = marketOrderMapper.selectById(orderId);
        return order != null && STATUS_ESCROW_PENDING.equals(order.getStatus());
    }

    @Transactional
    public void completeEscrowNoop(UUID orderId) {
        MarketOrder order = marketOrderMapper.selectByIdForUpdate(orderId);
        int updated = marketOrderMapper.markCancelledNoRefund(orderId);
        if (updated == 1) {
            restoreMarketSideCompensation(order);
        }
    }

    @Transactional
    public boolean markEscrowSucceeded(UUID orderId, UUID escrowTxnId) {
        int updated = marketOrderMapper.markEscrowSucceeded(orderId, escrowTxnId);
        if (updated != 1) {
            return false;
        }
        MarketOrder order = marketOrderMapper.selectByIdForUpdate(orderId);
        deliverPreloadedInventoryIfNeeded(order);
        return true;
    }

    @Transactional
    public boolean markEscrowCancelRefundPending(UUID orderId, UUID escrowTxnId) {
        return marketOrderMapper.markEscrowCancelRefundPending(orderId, escrowTxnId) == 1;
    }

    @Transactional
    public void markEscrowTerminalFailed(UUID orderId, String reason) {
        MarketOrder order = marketOrderMapper.selectByIdForUpdate(orderId);
        if (order == null) {
            return;
        }
        int updated = marketOrderMapper.markEscrowFailed(orderId);
        if (updated != 1 && STATUS_ESCROW_CANCEL_PENDING.equals(order.getStatus())) {
            updated = marketOrderMapper.markCancelledNoRefund(orderId);
        }
        if (updated == 1) {
            restoreMarketSideCompensation(order);
        }
    }

    @Transactional
    public boolean markReleaseSucceeded(UUID orderId, UUID releaseTxnId) {
        return marketOrderMapper.markReleaseSucceeded(orderId, releaseTxnId) == 1;
    }

    @Transactional
    public boolean markRefundSucceeded(UUID orderId, UUID refundTxnId) {
        MarketOrder order = marketOrderMapper.selectByIdForUpdate(orderId);
        if (order == null) {
            return false;
        }
        int updated = marketOrderMapper.markCancelledWithRefund(orderId, refundTxnId);
        if (updated != 1) {
            updated = marketOrderMapper.markDisputeRefundSucceeded(orderId, refundTxnId);
        }
        if (updated == 1) {
            restoreMarketSideCompensation(order);
            return true;
        }
        return false;
    }

    private void deliverPreloadedInventoryIfNeeded(MarketOrder order) {
        if (order == null || !DELIVERY_MODE_PRELOADED.equals(order.getDeliveryModeSnapshot())) {
            return;
        }
        Date deliveredAt = new Date();
        marketInventoryUnitMapper.markDeliveredByOrderIfReserved(order.getOrderId(), deliveredAt);
        marketOrderMapper.markDelivered(order.getOrderId(), Date.from(Instant.now().plus(24, ChronoUnit.HOURS)));
    }

    private void restoreMarketSideCompensation(MarketOrder order) {
        if (order == null) {
            return;
        }
        MarketListing listing = marketListingMapper.selectByIdForUpdate(order.getListingId());
        if (listing != null && isFiniteStock(listing)) {
            int nextAvailable = listing.getStockAvailable() + order.getQuantity();
            String nextStatus = STATUS_SOLD_OUT.equals(listing.getStatus()) && nextAvailable > 0
                    ? STATUS_ACTIVE
                    : listing.getStatus();
            marketListingMapper.adjustStock(
                    listing.getListingId(),
                    listing.getSellerUserId(),
                    0,
                    order.getQuantity(),
                    nextStatus
            );
        }
        if (DELIVERY_MODE_PRELOADED.equals(order.getDeliveryModeSnapshot())) {
            marketInventoryUnitMapper.releaseReservedByOrderIfNeeded(order.getOrderId());
        }
    }

    private boolean isFiniteStock(MarketListing listing) {
        return GOODS_TYPE_PHYSICAL.equals(listing.getGoodsType()) || STOCK_MODE_FINITE.equals(listing.getStockMode());
    }
}
