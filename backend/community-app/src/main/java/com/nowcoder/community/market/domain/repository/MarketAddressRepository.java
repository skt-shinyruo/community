package com.nowcoder.community.market.domain.repository;

import com.nowcoder.community.market.domain.model.MarketAddress;

import java.util.List;
import java.util.UUID;

public interface MarketAddressRepository {

    int insert(MarketAddress address);

    MarketAddress selectById(UUID addressId);

    List<MarketAddress> selectByUserId(UUID userId);

    int update(MarketAddress address);

    int clearDefaultByUserId(UUID userId);

    int softDelete(UUID addressId, UUID userId);
}
