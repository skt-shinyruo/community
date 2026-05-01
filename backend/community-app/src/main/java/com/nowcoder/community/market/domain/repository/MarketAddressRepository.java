package com.nowcoder.community.market.domain.repository;

import com.nowcoder.community.market.domain.model.MarketAddress;

import java.util.List;
import java.util.UUID;

public interface MarketAddressRepository {

    int save(MarketAddress address);

    MarketAddress findById(UUID addressId);

    List<MarketAddress> findByUserId(UUID userId);

    int saveChanges(MarketAddress address);

    int clearDefaultByUserId(UUID userId);

    int softDelete(UUID addressId, UUID userId);
}
