package com.nowcoder.community.wallet.api.action;

public interface WalletRewardActionApi {

    void issue(String requestId, int userId, long amount, String sourceType);

    void applyDelta(String requestId, int userId, long amount, String sourceType);
}
