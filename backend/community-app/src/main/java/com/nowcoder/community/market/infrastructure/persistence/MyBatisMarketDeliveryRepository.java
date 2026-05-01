package com.nowcoder.community.market.infrastructure.persistence;

import com.nowcoder.community.market.domain.model.MarketDelivery;
import com.nowcoder.community.market.domain.repository.MarketDeliveryRepository;
import com.nowcoder.community.market.infrastructure.persistence.dataobject.MarketDeliveryDataObject;
import com.nowcoder.community.market.infrastructure.persistence.mapper.MarketDeliveryMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public class MyBatisMarketDeliveryRepository implements MarketDeliveryRepository {

    private final MarketDeliveryMapper mapper;

    public MyBatisMarketDeliveryRepository(MarketDeliveryMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public int save(MarketDelivery delivery) {
        return mapper.insert(MarketDeliveryDataObject.from(delivery));
    }

    @Override
    public List<MarketDelivery> findByOrderId(UUID orderId) {
        return DomainRowAdapter.asDomainList(mapper.selectByOrderId(orderId));
    }
}
