package com.nowcoder.community.wallet.infrastructure.persistence;

import com.nowcoder.community.wallet.application.WalletTestCreditQuotaPort;
import com.nowcoder.community.wallet.infrastructure.persistence.dataobject.WalletTestCreditQuotaDataObject;
import com.nowcoder.community.wallet.infrastructure.persistence.mapper.WalletTestCreditQuotaMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public class MyBatisWalletTestCreditQuotaAdapter implements WalletTestCreditQuotaPort {

    private final WalletTestCreditQuotaMapper mapper;

    public MyBatisWalletTestCreditQuotaAdapter(WalletTestCreditQuotaMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Usage usage(UUID userId) {
        WalletTestCreditQuotaDataObject row = mapper.selectByUserId(userId);
        return row == null ? Usage.empty() : new Usage(row.getGrantedAmount(), row.getDiscardedAmount());
    }

    @Override
    public boolean tryReserveGrant(UUID userId, long amount, long quota) {
        ensureRow(userId);
        return mapper.incrementGranted(userId, amount, quota) == 1;
    }

    @Override
    public boolean tryReserveDiscard(UUID userId, long amount, long quota) {
        ensureRow(userId);
        return mapper.incrementDiscarded(userId, amount, quota) == 1;
    }

    private void ensureRow(UUID userId) {
        try {
            mapper.insertEmpty(userId);
        } catch (DuplicateKeyException ignored) {
            // Another request already owns the durable per-user quota row.
        }
    }
}
