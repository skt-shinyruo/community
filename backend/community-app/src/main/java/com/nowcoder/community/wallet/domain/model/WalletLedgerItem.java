package com.nowcoder.community.wallet.domain.model;

import java.util.Date;
import java.util.UUID;

public record WalletLedgerItem(
        UUID entryId,
        UUID txnId,
        UUID accountId,
        String direction,
        long entryAmount,
        long balanceAfter,
        Date entryCreateTime,
        String requestId,
        String txnType,
        String bizType,
        String bizId,
        String status,
        String remark,
        UUID counterpartUserId
) {
}
