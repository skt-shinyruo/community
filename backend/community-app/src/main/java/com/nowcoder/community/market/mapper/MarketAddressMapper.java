package com.nowcoder.community.market.mapper;

import com.nowcoder.community.market.entity.MarketAddress;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
@Mapper
public interface MarketAddressMapper {

    int insert(MarketAddress address);

    MarketAddress selectById(@Param("addressId") UUID addressId);

    List<MarketAddress> selectByUserId(@Param("userId") UUID userId);

    int update(MarketAddress address);

    int clearDefaultByUserId(@Param("userId") UUID userId);

    int softDelete(@Param("addressId") UUID addressId, @Param("userId") UUID userId);
}
