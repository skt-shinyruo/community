package com.nowcoder.community.wallet.model;

import java.util.UUID;

public record WalletTxnResult(UUID txnId, String status) {

    public WalletTxnResult(UUID txnId) {
        this(txnId, "SUCCEEDED");
    }
}
