package com.nowcoder.community.market.domain.repository;

import com.nowcoder.community.market.domain.model.MarketInventoryUnit;

import java.util.Date;
import java.util.List;
import java.util.UUID;

public interface MarketInventoryRepository {

    int insert(MarketInventoryUnit unit);

    int countAvailableByListingId(UUID listingId);

    List<MarketInventoryUnit> selectAvailableForUpdate(UUID listingId, int limit);

    List<MarketInventoryUnit> selectByReservedOrderId(UUID reservedOrderId);

    List<MarketInventoryUnit> selectByListingId(UUID listingId);

    MarketInventoryUnit selectById(UUID inventoryUnitId);

    int invalidateAvailable(UUID inventoryUnitId, UUID sellerUserId);

    int reserveForOrder(UUID inventoryUnitId, UUID reservedOrderId);

    int markDeliveredByOrder(UUID reservedOrderId, String status, Date deliveredAt);

    int markDeliveredByOrderIfReserved(UUID orderId, Date deliveredAt);

    int releaseReservedByOrder(UUID reservedOrderId);

    int releaseReservedByOrderIfNeeded(UUID orderId);
}
