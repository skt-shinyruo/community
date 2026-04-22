package com.nowcoder.community.wallet.api.action;

import com.nowcoder.community.wallet.api.model.WalletMarketTxnView;

import java.util.UUID;

public interface WalletMarketActionApi {

    WalletMarketTxnView escrowOrder(String requestId, UUID buyerUserId, long amount, String bizId);

    WalletMarketTxnView releaseOrder(String requestId, UUID sellerUserId, long amount, String bizId);

    WalletMarketTxnView refundOrder(String requestId, UUID buyerUserId, long amount, String bizId);
}
