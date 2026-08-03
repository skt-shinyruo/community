package com.nowcoder.community.wallet.infrastructure.persistence.mapper;

import com.nowcoder.community.wallet.infrastructure.persistence.dataobject.WalletTestCreditQuotaDataObject;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Mapper
@Repository
public interface WalletTestCreditQuotaMapper {

    WalletTestCreditQuotaDataObject selectByUserId(@Param("userId") UUID userId);

    int insertEmpty(@Param("userId") UUID userId);

    int incrementGranted(@Param("userId") UUID userId,
                         @Param("amount") long amount,
                         @Param("quota") long quota);

    int incrementDiscarded(@Param("userId") UUID userId,
                           @Param("amount") long amount,
                           @Param("quota") long quota);
}
