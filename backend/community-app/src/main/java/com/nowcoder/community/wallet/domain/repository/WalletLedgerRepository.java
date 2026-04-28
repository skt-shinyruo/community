package com.nowcoder.community.wallet.domain.repository;

import com.nowcoder.community.wallet.domain.model.WalletEntry;
import com.nowcoder.community.wallet.domain.model.WalletTxn;

import java.util.List;
import java.util.UUID;

public interface WalletLedgerRepository {

    WalletTxn findTxnByRequestId(String requestId);

    int insertTxn(WalletTxn txn);

    int markTxnSucceeded(UUID txnId);

    int insertEntry(WalletEntry entry);

    List<WalletEntry> findEntriesByTxnId(UUID txnId);
}
