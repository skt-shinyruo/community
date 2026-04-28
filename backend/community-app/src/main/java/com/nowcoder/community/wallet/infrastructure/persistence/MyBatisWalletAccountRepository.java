package com.nowcoder.community.wallet.infrastructure.persistence;

import com.nowcoder.community.wallet.domain.model.WalletAccount;
import com.nowcoder.community.wallet.domain.repository.WalletAccountRepository;
import com.nowcoder.community.wallet.infrastructure.persistence.dataobject.WalletAccountDataObject;
import com.nowcoder.community.wallet.infrastructure.persistence.mapper.WalletAccountMapper;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public class MyBatisWalletAccountRepository implements WalletAccountRepository {

    private final WalletAccountMapper mapper;

    public MyBatisWalletAccountRepository(WalletAccountMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public WalletAccount findByAccountId(UUID accountId) {
        return mapper.selectByAccountId(accountId);
    }

    @Override
    public WalletAccount findByOwner(String ownerType, UUID ownerId, String accountType) {
        return mapper.selectByOwner(ownerType, ownerId, accountType);
    }

    @Override
    public int insert(WalletAccount account) {
        return mapper.insert(WalletAccountDataObject.from(account));
    }

    @Override
    public int updateBalanceWithVersion(UUID accountId, long expectedVersion, long delta, String nextStatus) {
        return mapper.updateBalanceWithVersion(accountId, expectedVersion, delta, nextStatus);
    }
}
