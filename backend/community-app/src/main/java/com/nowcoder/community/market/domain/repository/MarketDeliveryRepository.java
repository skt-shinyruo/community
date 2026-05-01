package com.nowcoder.community.market.domain.repository;

import com.nowcoder.community.market.domain.model.MarketDelivery;

import java.util.List;
import java.util.UUID;

public interface MarketDeliveryRepository {

    int save(MarketDelivery delivery);

    List<MarketDelivery> findByOrderId(UUID orderId);
}
