package com.nowcoder.community.market.domain.repository;

import com.nowcoder.community.market.domain.model.MarketInventoryUnit;

import java.util.Date;
import java.util.List;
import java.util.UUID;

public interface MarketInventoryRepository {

    int save(MarketInventoryUnit unit);

    int countAvailableByListingId(UUID listingId);

    List<MarketInventoryUnit> lockAvailable(UUID listingId, int limit);

    List<MarketInventoryUnit> findByReservedOrderId(UUID reservedOrderId);

    List<MarketInventoryUnit> findByListingId(UUID listingId);

    MarketInventoryUnit findById(UUID inventoryUnitId);

    int invalidateAvailable(UUID inventoryUnitId, UUID sellerUserId);

    int reserveForOrder(UUID inventoryUnitId, UUID reservedOrderId);

    int markDeliveredByOrder(UUID reservedOrderId, String status, Date deliveredAt);

    int markDeliveredByOrderIfReserved(UUID orderId, Date deliveredAt);

    int releaseReservedByOrder(UUID reservedOrderId);

    int releaseReservedByOrderIfNeeded(UUID orderId);
}
