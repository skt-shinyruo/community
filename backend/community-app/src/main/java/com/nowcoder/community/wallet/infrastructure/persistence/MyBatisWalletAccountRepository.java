package com.nowcoder.community.wallet.infrastructure.persistence;

import com.nowcoder.community.wallet.domain.model.WalletAccount;
import com.nowcoder.community.wallet.domain.model.WalletAccountChange;
import com.nowcoder.community.wallet.domain.repository.CreationOutcome;
import com.nowcoder.community.wallet.domain.repository.WalletAccountRepository;
import com.nowcoder.community.wallet.infrastructure.persistence.dataobject.WalletAccountDataObject;
import com.nowcoder.community.wallet.infrastructure.persistence.mapper.WalletAccountMapper;
import org.springframework.dao.DuplicateKeyException;
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
        return toDomain(mapper.selectByAccountId(accountId));
    }

    @Override
    public WalletAccount findByOwner(String ownerType, UUID ownerId, String accountType) {
        return toDomain(mapper.selectByOwner(ownerType, ownerId, accountType));
    }

    @Override
    public CreationOutcome<WalletAccount> create(WalletAccount account) {
        try {
            return mapper.insert(WalletAccountDataObject.from(account)) == 1
                    ? CreationOutcome.created(account)
                    : CreationOutcome.conflict();
        } catch (DuplicateKeyException exception) {
            WalletAccount existing = toDomain(mapper.selectByOwner(
                    account.getOwnerType(),
                    account.getOwnerId(),
                    account.getAccountType()
            ));
            return existing == null
                    ? CreationOutcome.conflict()
                    : CreationOutcome.alreadyExists(existing);
        }
    }

    @Override
    public ApplyResult apply(WalletAccountChange change) {
        int updated = mapper.updateBalanceWithVersion(
                change.accountId(),
                change.expectedVersion(),
                change.delta(),
                change.nextStatus()
        );
        if (updated == 1) {
            return ApplyResult.APPLIED;
        }

        WalletAccount current = toDomain(mapper.selectByAccountId(change.accountId()));
        if (current == null) {
            return ApplyResult.NOT_FOUND;
        }
        if (current.getVersion() != change.expectedVersion()) {
            return ApplyResult.VERSION_CONFLICT;
        }
        if (change.delta() < 0L && current.getBalance() + change.delta() < 0L) {
            return ApplyResult.INSUFFICIENT_FUNDS;
        }
        return ApplyResult.VERSION_CONFLICT;
    }

    private WalletAccount toDomain(WalletAccountDataObject dataObject) {
        return dataObject == null ? null : dataObject.toDomain();
    }
}
