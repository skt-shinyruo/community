package com.nowcoder.community.wallet.infrastructure.api;

import com.nowcoder.community.wallet.api.action.WalletMarketActionApi;
import com.nowcoder.community.wallet.api.model.WalletMarketTxnView;
import com.nowcoder.community.wallet.application.WalletMarketApplicationService;
import com.nowcoder.community.wallet.application.command.WalletMarketTxnCommand;
import com.nowcoder.community.wallet.application.result.WalletMarketTxnResult;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class WalletMarketActionApiAdapter implements WalletMarketActionApi {

    private final WalletMarketApplicationService walletMarketApplicationService;

    public WalletMarketActionApiAdapter(WalletMarketApplicationService walletMarketApplicationService) {
        this.walletMarketApplicationService = walletMarketApplicationService;
    }

    @Override
    public WalletMarketTxnView escrowOrder(String requestId, UUID buyerUserId, long amount, String bizId) {
        return toView(walletMarketApplicationService.escrowOrder(new WalletMarketTxnCommand(requestId, buyerUserId, amount, bizId)));
    }

    @Override
    public WalletMarketTxnView releaseOrder(String requestId, UUID sellerUserId, long amount, String bizId) {
        return toView(walletMarketApplicationService.releaseOrder(new WalletMarketTxnCommand(requestId, sellerUserId, amount, bizId)));
    }

    @Override
    public WalletMarketTxnView refundOrder(String requestId, UUID buyerUserId, long amount, String bizId) {
        return toView(walletMarketApplicationService.refundOrder(new WalletMarketTxnCommand(requestId, buyerUserId, amount, bizId)));
    }

    private WalletMarketTxnView toView(WalletMarketTxnResult result) {
        return new WalletMarketTxnView(result.txnId(), result.txnType(), result.status(), result.amount(), result.bizId());
    }
}
