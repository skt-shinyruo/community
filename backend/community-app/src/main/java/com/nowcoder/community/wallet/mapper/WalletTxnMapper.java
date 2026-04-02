package com.nowcoder.community.wallet.mapper;

import com.nowcoder.community.wallet.entity.WalletTxn;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

@Repository
@Mapper
public interface WalletTxnMapper {

    WalletTxn selectByRequestId(@Param("requestId") String requestId);

    int insert(WalletTxn txn);
}
