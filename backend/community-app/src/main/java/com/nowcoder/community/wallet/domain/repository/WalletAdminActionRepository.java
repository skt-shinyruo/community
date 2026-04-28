package com.nowcoder.community.wallet.domain.repository;

import com.nowcoder.community.wallet.domain.model.WalletAdminAction;

import java.util.List;
import java.util.UUID;

public interface WalletAdminActionRepository {

    int insert(WalletAdminAction action);

    WalletAdminAction findByRequestId(String requestId);

    List<WalletAdminAction> findRecentByTargetAccountId(UUID targetAccountId, int limit);
}
