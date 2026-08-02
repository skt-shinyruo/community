package com.nowcoder.community.market.infrastructure.persistence;

import com.nowcoder.community.market.domain.model.MarketAddress;
import com.nowcoder.community.market.domain.repository.MarketAddressRepository;
import com.nowcoder.community.market.infrastructure.persistence.dataobject.MarketAddressDataObject;
import com.nowcoder.community.market.infrastructure.persistence.mapper.MarketAddressMapper;
import org.springframework.dao.DuplicateKeyException;
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
    public WriteResult save(MarketAddress address) {
        try {
            return writeResult(mapper.insert(MarketAddressDataObject.from(address)));
        } catch (DuplicateKeyException exception) {
            return WriteResult.DEFAULT_CONFLICT;
        }
    }

    @Override
    public MarketAddress findById(UUID addressId) {
        return mapper.selectById(addressId);
    }

    @Override
    public List<MarketAddress> findByUserId(UUID userId) {
        return DomainRowAdapter.asDomainList(mapper.selectByUserId(userId));
    }

    @Override
    public WriteResult saveChanges(MarketAddress address) {
        try {
            return writeResult(mapper.update(MarketAddressDataObject.from(address)));
        } catch (DuplicateKeyException exception) {
            return WriteResult.DEFAULT_CONFLICT;
        }
    }

    @Override
    public int clearDefaultByUserId(UUID userId) {
        return mapper.clearDefaultByUserId(userId);
    }

    @Override
    public int softDelete(UUID addressId, UUID userId) {
        return mapper.softDelete(addressId, userId);
    }

    private WriteResult writeResult(int affectedRows) {
        return affectedRows == 1 ? WriteResult.APPLIED : WriteResult.STALE;
    }
}
