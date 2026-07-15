package com.nowcoder.community.market.domain.repository;

import com.nowcoder.community.market.domain.model.MarketOrder;
import com.nowcoder.community.market.domain.model.MarketOrderTransition;

import java.util.Date;
import java.util.List;
import java.util.UUID;

public interface MarketOrderRepository {

    enum CreateStatus {
        CREATED,
        ALREADY_EXISTS,
        CONFLICT
    }

    record CreateResult(CreateStatus status, MarketOrder aggregate) {
    }

    enum ApplyStatus {
        APPLIED,
        STALE
    }

    CreateResult create(MarketOrder order);

    MarketOrder findById(UUID orderId);

    MarketOrder lockById(UUID orderId);

    MarketOrder findByRequestId(String requestId);

    MarketOrder lockByRequestId(String requestId);

    MarketOrder findByBuyerUserIdAndRequestId(UUID buyerUserId, String requestId);

    MarketOrder lockByBuyerUserIdAndRequestId(UUID buyerUserId, String requestId);

    List<MarketOrder> findByBuyerUserId(UUID buyerUserId);

    List<MarketOrder> findBySellerUserId(UUID sellerUserId);

    ApplyStatus apply(MarketOrderTransition transition);

    List<MarketOrder> findDueForAutoConfirm(Date asOf);

    List<MarketOrder> findWalletPendingOrders(int limit);
}
