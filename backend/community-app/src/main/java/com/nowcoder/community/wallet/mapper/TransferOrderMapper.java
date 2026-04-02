package com.nowcoder.community.wallet.mapper;

import com.nowcoder.community.wallet.entity.TransferOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

@Repository
@Mapper
public interface TransferOrderMapper {

    TransferOrder selectByRequestId(@Param("requestId") String requestId);

    int insert(TransferOrder order);
}
