package com.nowcoder.community.market.infrastructure.persistence;

import com.nowcoder.community.market.domain.model.MarketDispute;
import com.nowcoder.community.market.domain.repository.MarketDisputeRepository;
import com.nowcoder.community.market.domain.model.MarketDispute;
import com.nowcoder.community.market.infrastructure.persistence.mapper.MarketDisputeMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public class MyBatisMarketDisputeRepository implements MarketDisputeRepository {

    private final MarketDisputeMapper mapper;

    public MyBatisMarketDisputeRepository(MarketDisputeMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public int save(MarketDispute dispute) {
        return mapper.insert(dispute);
    }

    @Override
    public MarketDispute findById(UUID disputeId) {
        return mapper.selectById(disputeId);
    }

    @Override
    public MarketDispute lockById(UUID disputeId) {
        return mapper.selectByIdForUpdate(disputeId);
    }

    @Override
    public List<MarketDispute> findByOrderId(UUID orderId) {
        return mapper.selectByOrderId(orderId);
    }

    @Override
    public List<MarketDispute> findOpenDisputes() {
        return mapper.selectOpenDisputes();
    }

    @Override
    public int saveChanges(MarketDispute dispute) {
        return mapper.update(dispute);
    }
}
