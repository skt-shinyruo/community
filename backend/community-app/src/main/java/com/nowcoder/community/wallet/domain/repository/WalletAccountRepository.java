package com.nowcoder.community.wallet.domain.repository;

import com.nowcoder.community.wallet.domain.model.WalletAccount;
import com.nowcoder.community.wallet.domain.model.WalletAccountChange;

import java.util.UUID;

public interface WalletAccountRepository {

    enum ApplyResult {
        APPLIED,
        NOT_FOUND,
        VERSION_CONFLICT,
        INSUFFICIENT_FUNDS
    }

    WalletAccount findByAccountId(UUID accountId);

    WalletAccount findByOwner(String ownerType, UUID ownerId, String accountType);

    CreationOutcome<WalletAccount> create(WalletAccount account);

    ApplyResult apply(WalletAccountChange change);
}
