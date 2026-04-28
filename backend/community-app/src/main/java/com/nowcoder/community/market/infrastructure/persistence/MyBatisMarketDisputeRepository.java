package com.nowcoder.community.market.infrastructure.persistence;

import com.nowcoder.community.market.domain.model.MarketDispute;
import com.nowcoder.community.market.domain.repository.MarketDisputeRepository;
import com.nowcoder.community.market.infrastructure.persistence.dataobject.MarketDisputeDataObject;
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
    public int insert(MarketDispute dispute) {
        return mapper.insert(MarketDisputeDataObject.from(dispute));
    }

    @Override
    public MarketDispute selectById(UUID disputeId) {
        return mapper.selectById(disputeId);
    }

    @Override
    public List<MarketDispute> selectByOrderId(UUID orderId) {
        return DomainRowAdapter.asDomainList(mapper.selectByOrderId(orderId));
    }

    @Override
    public List<MarketDispute> selectOpenDisputes() {
        return DomainRowAdapter.asDomainList(mapper.selectOpenDisputes());
    }

    @Override
    public int update(MarketDispute dispute) {
        return mapper.update(MarketDisputeDataObject.from(dispute));
    }
}
