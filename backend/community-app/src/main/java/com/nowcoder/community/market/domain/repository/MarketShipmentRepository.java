package com.nowcoder.community.market.domain.repository;

import com.nowcoder.community.market.domain.model.MarketShipment;

import java.util.UUID;

public interface MarketShipmentRepository {

    int insert(MarketShipment shipment);

    MarketShipment selectByOrderId(UUID orderId);
}
