package com.nowcoder.community.market.infrastructure.persistence;

import com.nowcoder.community.market.domain.model.MarketOrder;
import com.nowcoder.community.market.domain.model.MarketOrderTransition;
import com.nowcoder.community.market.domain.repository.MarketOrderRepository;
import com.nowcoder.community.market.infrastructure.persistence.dataobject.MarketOrderDataObject;
import com.nowcoder.community.market.infrastructure.persistence.dataobject.MarketOrderTransitionDataObject;
import com.nowcoder.community.market.infrastructure.persistence.mapper.MarketOrderMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;
import java.util.UUID;

@Repository
public class MyBatisMarketOrderRepository implements MarketOrderRepository {

    private final MarketOrderMapper mapper;

    public MyBatisMarketOrderRepository(MarketOrderMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public CreateResult create(MarketOrder order) {
        try {
            return mapper.insert(MarketOrderDataObject.from(order)) == 1
                    ? new CreateResult(CreateStatus.CREATED, order)
                    : new CreateResult(CreateStatus.CONFLICT, null);
        } catch (DuplicateKeyException ignored) {
            MarketOrder existing = lockByBuyerUserIdAndRequestId(order.getBuyerUserId(), order.getRequestId());
            return existing == null
                    ? new CreateResult(CreateStatus.CONFLICT, null)
                    : new CreateResult(CreateStatus.ALREADY_EXISTS, existing);
        }
    }

    @Override
    public MarketOrder findById(UUID orderId) {
        return toDomain(mapper.selectById(orderId));
    }

    @Override
    public MarketOrder lockById(UUID orderId) {
        return toDomain(mapper.selectByIdForUpdate(orderId));
    }

    @Override
    public MarketOrder findByRequestId(String requestId) {
        return toDomain(mapper.selectByRequestId(requestId));
    }

    @Override
    public MarketOrder lockByRequestId(String requestId) {
        return toDomain(mapper.selectByRequestIdForUpdate(requestId));
    }

    @Override
    public MarketOrder findByBuyerUserIdAndRequestId(UUID buyerUserId, String requestId) {
        return toDomain(mapper.selectByBuyerUserIdAndRequestId(buyerUserId, requestId));
    }

    @Override
    public MarketOrder lockByBuyerUserIdAndRequestId(UUID buyerUserId, String requestId) {
        return toDomain(mapper.selectByBuyerUserIdAndRequestIdForUpdate(buyerUserId, requestId));
    }

    @Override
    public List<MarketOrder> findByBuyerUserId(UUID buyerUserId) {
        return toDomainList(mapper.selectByBuyerUserId(buyerUserId));
    }

    @Override
    public List<MarketOrder> findBySellerUserId(UUID sellerUserId) {
        return toDomainList(mapper.selectBySellerUserId(sellerUserId));
    }

    @Override
    public ApplyStatus apply(MarketOrderTransition transition) {
        int updated = mapper.apply(MarketOrderTransitionDataObject.from(transition));
        if (updated == 1) {
            return ApplyStatus.APPLIED;
        }
        if (updated == 0) {
            return ApplyStatus.STALE;
        }
        throw new IllegalStateException("market order transition updated unexpected row count: " + updated);
    }

    @Override
    public List<MarketOrder> findDueForAutoConfirm(Date asOf) {
        return toDomainList(mapper.selectDueForAutoConfirm(asOf));
    }

    @Override
    public List<MarketOrder> findWalletPendingOrders(int limit) {
        return toDomainList(mapper.selectWalletPendingOrders(limit));
    }

    private static MarketOrder toDomain(MarketOrderDataObject dataObject) {
        return dataObject == null ? null : dataObject.toDomain();
    }

    private static List<MarketOrder> toDomainList(List<MarketOrderDataObject> dataObjects) {
        if (dataObjects == null || dataObjects.isEmpty()) {
            return List.of();
        }
        return dataObjects.stream().map(MarketOrderDataObject::toDomain).toList();
    }
}
