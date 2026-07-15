package com.nowcoder.community.wallet.domain.repository;

import com.nowcoder.community.wallet.domain.model.WalletEntry;
import com.nowcoder.community.wallet.domain.model.WalletLedgerItem;
import com.nowcoder.community.wallet.domain.model.WalletTxn;

import java.util.List;
import java.util.UUID;

public interface WalletLedgerRepository {

    WalletTxn findTxnByRequestId(String requestId);

    CreationOutcome<WalletTxn> create(WalletTxn txn);

    int markTxnSucceeded(UUID txnId);

    int insertEntry(WalletEntry entry);

    List<WalletEntry> findEntriesByTxnId(UUID txnId);

    List<WalletLedgerItem> findRecentItemsByAccountId(UUID accountId, int limit);
}
