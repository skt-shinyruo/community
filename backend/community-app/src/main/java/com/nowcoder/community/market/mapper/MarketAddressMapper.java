package com.nowcoder.community.market.mapper;

import com.nowcoder.community.market.entity.MarketAddress;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@Mapper
public interface MarketAddressMapper {

    int insert(MarketAddress address);

    MarketAddress selectById(@Param("addressId") long addressId);

    List<MarketAddress> selectByUserId(@Param("userId") int userId);

    int update(MarketAddress address);

    int clearDefaultByUserId(@Param("userId") int userId);

    int softDelete(@Param("addressId") long addressId, @Param("userId") int userId);
}
