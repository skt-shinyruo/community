package com.nowcoder.community.wallet.controller.dto;

import com.nowcoder.community.wallet.application.result.WalletTransactionResult;

import java.util.Date;
import java.util.UUID;

public record WalletTransactionResponse(
        UUID txnId,
        String txnRef,
        String txnType,
        String bizType,
        String bizId,
        String status,
        long amount,
        long balanceAfter,
        String counterpartLabel,
        String remark,
        Date createTime
) {

    public static WalletTransactionResponse from(WalletTransactionResult result) {
        return new WalletTransactionResponse(
                result.txnId(),
                result.txnRef(),
                result.txnType(),
                result.bizType(),
                result.bizId(),
                result.status(),
                result.amount(),
                result.balanceAfter(),
                result.counterpartLabel(),
                result.remark(),
                result.createTime()
        );
    }
}
