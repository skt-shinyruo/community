package com.nowcoder.community.wallet.infrastructure.persistence.mapper;

import com.nowcoder.community.wallet.infrastructure.persistence.dataobject.WalletEntryDataObject;
import com.nowcoder.community.wallet.infrastructure.persistence.dataobject.WalletLedgerItemDataObject;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
@Mapper
public interface WalletEntryMapper {

    int insert(WalletEntryDataObject entry);

    List<WalletEntryDataObject> selectByTxnId(@Param("txnId") UUID txnId);

    List<WalletLedgerItemDataObject> selectRecentItemsByAccountId(@Param("accountId") UUID accountId, @Param("limit") int limit);
}
