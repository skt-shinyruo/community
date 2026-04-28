package com.nowcoder.community.wallet.domain.service;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.wallet.domain.model.WalletPosting;
import com.nowcoder.community.wallet.domain.model.WalletTxn;
import com.nowcoder.community.wallet.domain.model.WalletTxnType;
import com.nowcoder.community.wallet.exception.WalletErrorCode;

import java.util.Date;
import java.util.List;
import java.util.UUID;

public final class WalletLedgerDomainService {

    public static final String TXN_STATUS_PENDING = "PENDING";
    public static final String TXN_STATUS_SUCCEEDED = "SUCCEEDED";

    public void validateBalancedPostings(List<WalletPosting> postings) {
        long debitTotal = postings.stream()
                .filter(posting -> WalletAccountDomainService.DIRECTION_DEBIT.equals(posting.direction()))
                .mapToLong(WalletPosting::amount)
                .sum();
        long creditTotal = postings.stream()
                .filter(posting -> WalletAccountDomainService.DIRECTION_CREDIT.equals(posting.direction()))
                .mapToLong(WalletPosting::amount)
                .sum();
        if (debitTotal <= 0 || debitTotal != creditTotal) {
            throw new BusinessException(WalletErrorCode.TXN_NOT_BALANCED, "wallet txn is not balanced");
        }
    }

    public WalletTxn newTxn(UUID txnId,
                            String requestId,
                            WalletTxnType txnType,
                            String bizType,
                            String bizId,
                            long amount,
                            Date createTime) {
        WalletTxn txn = new WalletTxn();
        txn.setTxnId(txnId);
        txn.setRequestId(requestId);
        txn.setTxnType(txnType.name());
        txn.setBizType(bizType);
        txn.setBizId(bizId);
        txn.setStatus(TXN_STATUS_PENDING);
        txn.setAmount(amount);
        txn.setCreateTime(createTime);
        return txn;
    }
}
