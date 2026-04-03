package com.nowcoder.community.wallet.api.action;

import com.nowcoder.community.wallet.api.model.WalletMarketTxnView;

public interface WalletMarketActionApi {

    WalletMarketTxnView escrowOrder(String requestId, int buyerUserId, long amount, String bizId);

    WalletMarketTxnView releaseOrder(String requestId, int sellerUserId, long amount, String bizId);

    WalletMarketTxnView refundOrder(String requestId, int buyerUserId, long amount, String bizId);
}
