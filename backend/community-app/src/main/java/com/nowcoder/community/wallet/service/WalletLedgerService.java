package com.nowcoder.community.wallet.service;

import com.nowcoder.community.common.id.UuidV7Generator;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.wallet.entity.WalletAccount;
import com.nowcoder.community.wallet.entity.WalletEntry;
import com.nowcoder.community.wallet.entity.WalletTxn;
import com.nowcoder.community.wallet.exception.WalletErrorCode;
import com.nowcoder.community.wallet.mapper.WalletEntryMapper;
import com.nowcoder.community.wallet.mapper.WalletTxnMapper;
import com.nowcoder.community.wallet.model.WalletPosting;
import com.nowcoder.community.wallet.model.WalletTxnResult;
import com.nowcoder.community.wallet.model.WalletTxnType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class WalletLedgerService {

    private static final String TXN_STATUS_PENDING = "PENDING";
    private static final String TXN_STATUS_SUCCEEDED = "SUCCEEDED";
    private static final String DIRECTION_DEBIT = "DEBIT";
    private static final String DIRECTION_CREDIT = "CREDIT";

    private final WalletAccountService walletAccountService;
    private final WalletTxnMapper walletTxnMapper;
    private final WalletEntryMapper walletEntryMapper;
    private final UuidV7Generator idGenerator;

    @Autowired
    public WalletLedgerService(WalletAccountService walletAccountService,
                               WalletTxnMapper walletTxnMapper,
                               WalletEntryMapper walletEntryMapper) {
        this(walletAccountService, walletTxnMapper, walletEntryMapper, new UuidV7Generator());
    }

    WalletLedgerService(WalletAccountService walletAccountService,
                        WalletTxnMapper walletTxnMapper,
                        WalletEntryMapper walletEntryMapper,
                        UuidV7Generator idGenerator) {
        this.walletAccountService = walletAccountService;
        this.walletTxnMapper = walletTxnMapper;
        this.walletEntryMapper = walletEntryMapper;
        this.idGenerator = idGenerator;
    }

    public UUID ensureUserWallet(UUID userId) {
        return walletAccountService.ensureUserWallet(userId);
    }

    public UUID ensureSystemAccount(String accountType) {
        return walletAccountService.ensureSystemAccount(accountType);
    }

    public long balanceOfUser(UUID userId) {
        return walletAccountService.balanceOfUser(userId);
    }

    public List<WalletEntry> entriesOfTxn(UUID txnId) {
        return walletEntryMapper.selectByTxnId(txnId);
    }

    @Transactional
    public WalletTxnResult post(String requestId, WalletTxnType txnType, List<WalletPosting> postings) {
        validateRequest(requestId, txnType, postings);
        requireBalanced(requestId, postings);

        WalletTxn existing = walletTxnMapper.selectByRequestId(requestId);
        if (existing != null) {
            return new WalletTxnResult(existing.getTxnId(), existing.getStatus());
        }

        WalletTxn txn = new WalletTxn();
        txn.setTxnId(idGenerator.next());
        txn.setRequestId(requestId);
        txn.setTxnType(txnType.name());
        txn.setBizType(txnType.name());
        txn.setBizId(requestId);
        txn.setStatus(TXN_STATUS_PENDING);
        txn.setAmount(postings.stream().mapToLong(WalletPosting::amount).sum() / 2);
        try {
            walletTxnMapper.insert(txn);
        } catch (DataIntegrityViolationException ex) {
            WalletTxn duplicated = walletTxnMapper.selectByRequestId(requestId);
            if (duplicated != null) {
                return new WalletTxnResult(duplicated.getTxnId(), duplicated.getStatus());
            }
            throw ex;
        }

        for (WalletPosting posting : postings) {
            WalletAccount account = walletAccountService.lock(posting.accountId());
            long nextBalance = walletAccountService.apply(account, walletAccountService.deltaOf(account, posting));

            WalletEntry entry = new WalletEntry();
            entry.setEntryId(idGenerator.next());
            entry.setTxnId(txn.getTxnId());
            entry.setAccountId(account.getAccountId());
            entry.setDirection(posting.direction());
            entry.setAmount(posting.amount());
            entry.setBalanceAfter(nextBalance);
            walletEntryMapper.insert(entry);
        }

        walletTxnMapper.markSucceeded(txn.getTxnId());
        return new WalletTxnResult(txn.getTxnId(), TXN_STATUS_SUCCEEDED);
    }

    private void validateRequest(String requestId, WalletTxnType txnType, List<WalletPosting> postings) {
        if (requestId == null || requestId.isBlank()) {
            throw new BusinessException(WalletErrorCode.INVALID_REQUEST, "requestId must not be blank");
        }
        if (txnType == null) {
            throw new BusinessException(WalletErrorCode.INVALID_REQUEST, "txnType must not be null");
        }
        if (postings == null || postings.size() < 2) {
            throw new BusinessException(WalletErrorCode.INVALID_REQUEST, "postings must contain at least two entries");
        }
    }

    private void requireBalanced(String requestId, List<WalletPosting> postings) {
        long debitTotal = postings.stream()
                .filter(posting -> DIRECTION_DEBIT.equals(posting.direction()))
                .mapToLong(WalletPosting::amount)
                .sum();
        long creditTotal = postings.stream()
                .filter(posting -> DIRECTION_CREDIT.equals(posting.direction()))
                .mapToLong(WalletPosting::amount)
                .sum();
        if (debitTotal <= 0 || debitTotal != creditTotal) {
            throw new BusinessException(WalletErrorCode.TXN_NOT_BALANCED, "wallet txn is not balanced: requestId=" + requestId);
        }
    }
}
