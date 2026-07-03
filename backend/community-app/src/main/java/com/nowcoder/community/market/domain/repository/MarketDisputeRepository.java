package com.nowcoder.community.market.domain.repository;

import com.nowcoder.community.market.domain.model.MarketDispute;

import java.util.List;
import java.util.UUID;

public interface MarketDisputeRepository {

    int save(MarketDispute dispute);

    MarketDispute findById(UUID disputeId);

    MarketDispute lockById(UUID disputeId);

    List<MarketDispute> findByOrderId(UUID orderId);

    List<MarketDispute> findOpenDisputes();

    int saveChanges(MarketDispute dispute);
}
