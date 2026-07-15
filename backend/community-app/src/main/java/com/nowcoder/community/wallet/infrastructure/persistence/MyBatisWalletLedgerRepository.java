package com.nowcoder.community.wallet.infrastructure.persistence;

import com.nowcoder.community.wallet.domain.model.WalletEntry;
import com.nowcoder.community.wallet.domain.model.WalletLedgerItem;
import com.nowcoder.community.wallet.domain.model.WalletTxn;
import com.nowcoder.community.wallet.domain.repository.CreationOutcome;
import com.nowcoder.community.wallet.domain.repository.WalletLedgerRepository;
import com.nowcoder.community.wallet.infrastructure.persistence.dataobject.WalletEntryDataObject;
import com.nowcoder.community.wallet.infrastructure.persistence.dataobject.WalletLedgerItemDataObject;
import com.nowcoder.community.wallet.infrastructure.persistence.dataobject.WalletTxnDataObject;
import com.nowcoder.community.wallet.infrastructure.persistence.mapper.WalletEntryMapper;
import com.nowcoder.community.wallet.infrastructure.persistence.mapper.WalletTxnMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Repository
public class MyBatisWalletLedgerRepository implements WalletLedgerRepository {

    private final WalletTxnMapper txnMapper;
    private final WalletEntryMapper entryMapper;

    public MyBatisWalletLedgerRepository(WalletTxnMapper txnMapper, WalletEntryMapper entryMapper) {
        this.txnMapper = txnMapper;
        this.entryMapper = entryMapper;
    }

    @Override
    public WalletTxn findTxnByRequestId(String requestId) {
        return txnMapper.selectByRequestId(requestId);
    }

    @Override
    public CreationOutcome<WalletTxn> create(WalletTxn txn) {
        try {
            return txnMapper.insert(WalletTxnDataObject.from(txn)) == 1
                    ? CreationOutcome.created(txn)
                    : CreationOutcome.conflict();
        } catch (DuplicateKeyException exception) {
            WalletTxn existing = txnMapper.selectByRequestId(txn.getRequestId());
            return existing == null
                    ? CreationOutcome.conflict()
                    : CreationOutcome.alreadyExists(existing);
        }
    }

    @Override
    public int markTxnSucceeded(UUID txnId) {
        return txnMapper.markSucceeded(txnId);
    }

    @Override
    public int insertEntry(WalletEntry entry) {
        return entryMapper.insert(WalletEntryDataObject.from(entry));
    }

    @Override
    public List<WalletEntry> findEntriesByTxnId(UUID txnId) {
        return new ArrayList<>(entryMapper.selectByTxnId(txnId));
    }

    @Override
    public List<WalletLedgerItem> findRecentItemsByAccountId(UUID accountId, int limit) {
        return entryMapper.selectRecentItemsByAccountId(accountId, limit).stream()
                .map(WalletLedgerItemDataObject::toDomain)
                .toList();
    }
}
