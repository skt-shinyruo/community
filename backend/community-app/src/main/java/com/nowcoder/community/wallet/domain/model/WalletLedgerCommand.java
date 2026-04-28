package com.nowcoder.community.wallet.domain.model;

import java.util.List;

public record WalletLedgerCommand(
        String requestId,
        WalletTxnType txnType,
        String bizType,
        String bizId,
        List<WalletPosting> postings
) {
}
