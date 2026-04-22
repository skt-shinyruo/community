package com.nowcoder.community.wallet.api.action;

import java.util.UUID;

public interface WalletRewardActionApi {

    void issue(String requestId, UUID userId, long amount, String sourceType);

    void applyDelta(String requestId, UUID userId, long amount, String sourceType);
}
