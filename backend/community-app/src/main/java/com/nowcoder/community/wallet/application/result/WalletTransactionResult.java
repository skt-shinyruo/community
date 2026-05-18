package com.nowcoder.community.wallet.application.result;

import java.util.Date;
import java.util.UUID;

public record WalletTransactionResult(
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
}
