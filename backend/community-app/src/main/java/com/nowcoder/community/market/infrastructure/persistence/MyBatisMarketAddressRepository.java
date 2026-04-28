package com.nowcoder.community.market.infrastructure.persistence;

import com.nowcoder.community.market.domain.model.MarketAddress;
import com.nowcoder.community.market.domain.repository.MarketAddressRepository;
import com.nowcoder.community.market.infrastructure.persistence.dataobject.MarketAddressDataObject;
import com.nowcoder.community.market.infrastructure.persistence.mapper.MarketAddressMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public class MyBatisMarketAddressRepository implements MarketAddressRepository {

    private final MarketAddressMapper mapper;

    public MyBatisMarketAddressRepository(MarketAddressMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public int insert(MarketAddress address) {
        return mapper.insert(MarketAddressDataObject.from(address));
    }

    @Override
    public MarketAddress selectById(UUID addressId) {
        return mapper.selectById(addressId);
    }

    @Override
    public List<MarketAddress> selectByUserId(UUID userId) {
        return DomainRowAdapter.asDomainList(mapper.selectByUserId(userId));
    }

    @Override
    public int update(MarketAddress address) {
        return mapper.update(MarketAddressDataObject.from(address));
    }

    @Override
    public int clearDefaultByUserId(UUID userId) {
        return mapper.clearDefaultByUserId(userId);
    }

    @Override
    public int softDelete(UUID addressId, UUID userId) {
        return mapper.softDelete(addressId, userId);
    }
}
