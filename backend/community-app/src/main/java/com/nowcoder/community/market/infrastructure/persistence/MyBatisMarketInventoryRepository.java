package com.nowcoder.community.market.infrastructure.persistence;

import com.nowcoder.community.market.domain.model.MarketInventoryUnit;
import com.nowcoder.community.market.domain.repository.MarketInventoryRepository;
import com.nowcoder.community.market.infrastructure.persistence.dataobject.MarketInventoryUnitDataObject;
import com.nowcoder.community.market.infrastructure.persistence.mapper.MarketInventoryUnitMapper;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;
import java.util.UUID;

@Repository
public class MyBatisMarketInventoryRepository implements MarketInventoryRepository {

    private final MarketInventoryUnitMapper mapper;

    public MyBatisMarketInventoryRepository(MarketInventoryUnitMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public int save(MarketInventoryUnit unit) {
        return mapper.insert(MarketInventoryUnitDataObject.from(unit));
    }

    @Override
    public int countAvailableByListingId(UUID listingId) {
        return mapper.countAvailableByListingId(listingId);
    }

    @Override
    public List<MarketInventoryUnit> lockAvailable(UUID listingId, int limit) {
        return DomainRowAdapter.asDomainList(mapper.selectAvailableForUpdate(listingId, limit));
    }

    @Override
    public List<MarketInventoryUnit> findByReservedOrderId(UUID reservedOrderId) {
        return DomainRowAdapter.asDomainList(mapper.selectByReservedOrderId(reservedOrderId));
    }

    @Override
    public List<MarketInventoryUnit> findByListingId(UUID listingId) {
        return DomainRowAdapter.asDomainList(mapper.selectByListingId(listingId));
    }

    @Override
    public MarketInventoryUnit findById(UUID inventoryUnitId) {
        return mapper.selectById(inventoryUnitId);
    }

    @Override
    public int invalidateAvailable(UUID inventoryUnitId, UUID sellerUserId) {
        return mapper.invalidateAvailable(inventoryUnitId, sellerUserId);
    }

    @Override
    public int reserveForOrder(UUID inventoryUnitId, UUID reservedOrderId) {
        return mapper.reserveForOrder(inventoryUnitId, reservedOrderId);
    }

    @Override
    public int markDeliveredByOrder(UUID reservedOrderId, String status, Date deliveredAt) {
        return mapper.markDeliveredByOrder(reservedOrderId, status, deliveredAt);
    }

    @Override
    public int markDeliveredByOrderIfReserved(UUID orderId, Date deliveredAt) {
        return mapper.markDeliveredByOrderIfReserved(orderId, deliveredAt);
    }

    @Override
    public int releaseReservedByOrder(UUID reservedOrderId) {
        return mapper.releaseReservedByOrder(reservedOrderId);
    }

    @Override
    public int releaseReservedByOrderIfNeeded(UUID orderId) {
        return mapper.releaseReservedByOrderIfNeeded(orderId);
    }
}
