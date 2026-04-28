package com.nowcoder.community.market.domain.repository;

import com.nowcoder.community.market.domain.model.MarketDispute;

import java.util.List;
import java.util.UUID;

public interface MarketDisputeRepository {

    int insert(MarketDispute dispute);

    MarketDispute selectById(UUID disputeId);

    List<MarketDispute> selectByOrderId(UUID orderId);

    List<MarketDispute> selectOpenDisputes();

    int update(MarketDispute dispute);
}
