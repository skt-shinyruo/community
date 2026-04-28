package com.nowcoder.community.market.infrastructure.persistence.mapper;

import com.nowcoder.community.market.infrastructure.persistence.dataobject.MarketAddressDataObject;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
@Mapper
public interface MarketAddressMapper {

    int insert(MarketAddressDataObject address);

    MarketAddressDataObject selectById(@Param("addressId") UUID addressId);

    List<MarketAddressDataObject> selectByUserId(@Param("userId") UUID userId);

    int update(MarketAddressDataObject address);

    int clearDefaultByUserId(@Param("userId") UUID userId);

    int softDelete(@Param("addressId") UUID addressId, @Param("userId") UUID userId);
}
