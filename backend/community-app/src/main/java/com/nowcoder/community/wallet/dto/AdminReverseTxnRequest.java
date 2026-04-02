package com.nowcoder.community.wallet.dto;

import jakarta.validation.constraints.NotBlank;

public class AdminReverseTxnRequest {

    @NotBlank
    private String txnRef;

    @NotBlank
    private String reason;

    public String getTxnRef() {
        return txnRef;
    }

    public void setTxnRef(String txnRef) {
        this.txnRef = txnRef;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
