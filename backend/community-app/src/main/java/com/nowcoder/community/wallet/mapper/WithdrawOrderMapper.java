package com.nowcoder.community.wallet.mapper;

import com.nowcoder.community.wallet.entity.WithdrawOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

@Repository
@Mapper
public interface WithdrawOrderMapper {

    WithdrawOrder selectByRequestId(@Param("requestId") String requestId);

    int insert(WithdrawOrder order);
}
