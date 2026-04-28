package com.nowcoder.community.wallet.infrastructure.persistence.mapper;

import com.nowcoder.community.wallet.infrastructure.persistence.dataobject.WalletAccountDataObject;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
@Mapper
public interface WalletAccountMapper {

    WalletAccountDataObject selectByAccountId(@Param("accountId") UUID accountId);

    WalletAccountDataObject selectByOwner(@Param("ownerType") String ownerType,
                                @Param("ownerId") UUID ownerId,
                                @Param("accountType") String accountType);

    int insert(WalletAccountDataObject account);

    int updateBalanceWithVersion(@Param("accountId") UUID accountId,
                                 @Param("expectedVersion") long expectedVersion,
                                 @Param("delta") long delta,
                                 @Param("nextStatus") String nextStatus);
}
