package com.nowcoder.community.market.infrastructure.persistence;

import com.nowcoder.community.market.domain.model.MarketShipment;
import com.nowcoder.community.market.domain.repository.MarketShipmentRepository;
import com.nowcoder.community.market.infrastructure.persistence.dataobject.MarketShipmentDataObject;
import com.nowcoder.community.market.infrastructure.persistence.mapper.MarketShipmentMapper;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public class MyBatisMarketShipmentRepository implements MarketShipmentRepository {

    private final MarketShipmentMapper mapper;

    public MyBatisMarketShipmentRepository(MarketShipmentMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public int save(MarketShipment shipment) {
        return mapper.insert(MarketShipmentDataObject.from(shipment));
    }

    @Override
    public MarketShipment findByOrderId(UUID orderId) {
        return mapper.selectByOrderId(orderId);
    }
}
