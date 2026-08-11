package com.nowcoder.community.wallet.domain.service;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.wallet.domain.model.WalletPosting;
import com.nowcoder.community.wallet.domain.model.WalletTxn;
import com.nowcoder.community.wallet.domain.model.WalletTxnType;
import com.nowcoder.community.wallet.exception.WalletErrorCode;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class WalletLedgerDomainService {

    public static final String TXN_STATUS_PENDING = "PENDING";
    public static final String TXN_STATUS_SUCCEEDED = "SUCCEEDED";

    public void validateBalancedPostings(List<WalletPosting> postings) {
        long debitTotal = totalOf(postings, WalletAccountDomainService.DIRECTION_DEBIT);
        long creditTotal = totalOf(postings, WalletAccountDomainService.DIRECTION_CREDIT);
        if (debitTotal <= 0 || debitTotal != creditTotal) {
            throw new BusinessException(WalletErrorCode.TXN_NOT_BALANCED, "wallet txn is not balanced");
        }
    }

    /**
     * Reduce every account to one net posting before acquiring database locks.
     * This makes lock acquisition independent of the caller's posting order and
     * prevents the same account version from being advanced multiple times.
     */
    public List<WalletPosting> canonicalizePostings(List<WalletPosting> postings) {
        Map<UUID, Totals> totalsByAccount = new LinkedHashMap<>();
        for (WalletPosting posting : postings) {
            Totals totals = totalsByAccount.computeIfAbsent(posting.accountId(), ignored -> new Totals());
            if (WalletAccountDomainService.DIRECTION_DEBIT.equals(posting.direction())) {
                totals.debit = WalletAmountPolicy.checkedAdd(totals.debit, posting.amount());
            } else {
                totals.credit = WalletAmountPolicy.checkedAdd(totals.credit, posting.amount());
            }
        }
        List<WalletPosting> canonical = new java.util.ArrayList<>();
        for (Map.Entry<UUID, Totals> entry : totalsByAccount.entrySet()) {
            Totals totals = entry.getValue();
            if (totals.debit > totals.credit) {
                canonical.add(WalletPosting.debit(
                        entry.getKey(),
                        Math.subtractExact(totals.debit, totals.credit)
                ));
            } else if (totals.credit > totals.debit) {
                canonical.add(WalletPosting.credit(
                        entry.getKey(),
                        Math.subtractExact(totals.credit, totals.debit)
                ));
            }
        }
        return List.copyOf(canonical);
    }

    public long balancedAmountOf(List<WalletPosting> postings) {
        long debitTotal = totalOf(postings, WalletAccountDomainService.DIRECTION_DEBIT);
        WalletAmountPolicy.validateAmount(debitTotal);
        return debitTotal;
    }

    private long totalOf(List<WalletPosting> postings, String direction) {
        long total = 0L;
        for (WalletPosting posting : postings) {
            if (direction.equals(posting.direction())) {
                total = WalletAmountPolicy.checkedAdd(total, posting.amount());
            }
        }
        return total;
    }

    private static final class Totals {
        private long debit;
        private long credit;
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
