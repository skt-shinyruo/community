package com.nowcoder.community.wallet.domain.repository;

import com.nowcoder.community.wallet.domain.model.WalletAccount;

import java.util.UUID;

public interface WalletAccountRepository {

    WalletAccount findByAccountId(UUID accountId);

    WalletAccount findByOwner(String ownerType, UUID ownerId, String accountType);

    int insert(WalletAccount account);

    int updateBalanceWithVersion(UUID accountId, long expectedVersion, long delta, String nextStatus);
}
