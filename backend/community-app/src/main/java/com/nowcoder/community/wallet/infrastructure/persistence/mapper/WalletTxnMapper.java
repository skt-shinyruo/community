package com.nowcoder.community.wallet.infrastructure.persistence.mapper;

import com.nowcoder.community.wallet.infrastructure.persistence.dataobject.WalletTxnDataObject;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
@Mapper
public interface WalletTxnMapper {

    WalletTxnDataObject selectByRequestId(@Param("requestId") String requestId);

    int insert(WalletTxnDataObject txn);

    int markSucceeded(@Param("txnId") UUID txnId);
}
