package com.nowcoder.community.wallet.model;

public record WalletTxnResult(long txnId, String status) {

    public WalletTxnResult(long txnId) {
        this(txnId, "SUCCEEDED");
    }
}
