package com.nowcoder.community.wallet.application;

import com.nowcoder.community.common.id.UuidV7Generator;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.wallet.domain.model.WalletAccount;
import com.nowcoder.community.wallet.domain.model.WalletEntry;
import com.nowcoder.community.wallet.domain.model.WalletLedgerCommand;
import com.nowcoder.community.wallet.domain.model.WalletPosting;
import com.nowcoder.community.wallet.domain.model.WalletTxn;
import com.nowcoder.community.wallet.domain.model.WalletTxnType;
import com.nowcoder.community.wallet.domain.repository.WalletLedgerRepository;
import com.nowcoder.community.wallet.domain.service.WalletLedgerDomainService;
import com.nowcoder.community.wallet.exception.WalletErrorCode;
import com.nowcoder.community.wallet.application.result.WalletTxnResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
public class WalletLedgerApplicationService {

    private final WalletAccountApplicationService walletAccountService;
    private final WalletLedgerRepository walletLedgerRepository;
    private final WalletLedgerDomainService domainService;
    private final UuidV7Generator idGenerator;

    @Autowired
    public WalletLedgerApplicationService(WalletAccountApplicationService walletAccountService,
                                          WalletLedgerRepository walletLedgerRepository) {
        this(walletAccountService, walletLedgerRepository, new WalletLedgerDomainService(), new UuidV7Generator());
    }

    WalletLedgerApplicationService(WalletAccountApplicationService walletAccountService,
                                   WalletLedgerRepository walletLedgerRepository,
                                   WalletLedgerDomainService domainService,
                                   UuidV7Generator idGenerator) {
        this.walletAccountService = walletAccountService;
        this.walletLedgerRepository = walletLedgerRepository;
        this.domainService = domainService;
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
        return walletLedgerRepository.findEntriesByTxnId(txnId);
    }

    @Transactional
    public WalletTxnResult post(String requestId, WalletTxnType txnType, List<WalletPosting> postings) {
        return post(new WalletLedgerCommand(requestId, txnType, defaultBizType(txnType), requestId, postings));
    }

    @Transactional
    public WalletTxnResult post(String requestId, WalletTxnType txnType, String bizId, List<WalletPosting> postings) {
        return post(new WalletLedgerCommand(requestId, txnType, defaultBizType(txnType), bizId, postings));
    }

    @Transactional
    public WalletTxnResult post(WalletLedgerCommand command) {
        validateRequest(command);
        String requestId = command.requestId();
        WalletTxnType txnType = command.txnType();
        String normalizedBizType = validateText(command.bizType(), "bizType");
        String normalizedBizId = validateText(command.bizId(), "bizId");
        List<WalletPosting> postings = command.postings();
        domainService.validateBalancedPostings(postings);

        WalletTxn existing = walletLedgerRepository.findTxnByRequestId(requestId);
        if (existing != null) {
            ensureReplayMatches(existing, txnType, normalizedBizType, normalizedBizId, postings);
            return new WalletTxnResult(existing.getTxnId(), existing.getStatus());
        }

        WalletTxn txn = domainService.newTxn(
                idGenerator.next(),
                requestId,
                txnType,
                normalizedBizType,
                normalizedBizId,
                amountOf(postings),
                new Date()
        );
        try {
            walletLedgerRepository.insertTxn(txn);
        } catch (DataIntegrityViolationException ex) {
            WalletTxn duplicated = walletLedgerRepository.findTxnByRequestId(requestId);
            if (duplicated != null) {
                ensureReplayMatches(duplicated, txnType, normalizedBizType, normalizedBizId, postings);
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
            walletLedgerRepository.insertEntry(entry);
        }

        walletLedgerRepository.markTxnSucceeded(txn.getTxnId());
        return new WalletTxnResult(txn.getTxnId(), WalletLedgerDomainService.TXN_STATUS_SUCCEEDED);
    }

    private long amountOf(List<WalletPosting> postings) {
        return postings.stream().mapToLong(WalletPosting::amount).sum() / 2;
    }

    private String defaultBizType(WalletTxnType txnType) {
        return txnType == null ? null : txnType.name();
    }

    private String validateText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(WalletErrorCode.INVALID_REQUEST, fieldName + " must not be blank");
        }
        return value.trim();
    }

    private void ensureReplayMatches(WalletTxn existing,
                                     WalletTxnType txnType,
                                     String bizType,
                                     String bizId,
                                     List<WalletPosting> postings) {
        boolean matches = Objects.equals(existing.getTxnType(), txnType.name())
                && Objects.equals(existing.getBizType(), bizType)
                && Objects.equals(existing.getBizId(), bizId)
                && existing.getAmount() == amountOf(postings)
                && postingFingerprintMatches(existing.getTxnId(), postings);
        if (!matches) {
            throw new BusinessException(
                    WalletErrorCode.REQUEST_REPLAY_CONFLICT,
                    "wallet request replay conflict: requestId=" + existing.getRequestId()
            );
        }
    }

    private boolean postingFingerprintMatches(UUID txnId, List<WalletPosting> postings) {
        return entryFingerprint(walletLedgerRepository.findEntriesByTxnId(txnId)).equals(postingFingerprint(postings));
    }

    private Map<String, Long> postingFingerprint(List<WalletPosting> postings) {
        Map<String, Long> fingerprint = new HashMap<>();
        for (WalletPosting posting : postings) {
            fingerprint.merge(posting.accountId() + ":" + posting.direction() + ":" + posting.amount(), 1L, Long::sum);
        }
        return fingerprint;
    }

    private Map<String, Long> entryFingerprint(List<WalletEntry> entries) {
        Map<String, Long> fingerprint = new HashMap<>();
        for (WalletEntry entry : entries) {
            fingerprint.merge(entry.getAccountId() + ":" + entry.getDirection() + ":" + entry.getAmount(), 1L, Long::sum);
        }
        return fingerprint;
    }

    private void validateRequest(WalletLedgerCommand command) {
        if (command == null) {
            throw new BusinessException(WalletErrorCode.INVALID_REQUEST, "wallet ledger command must not be null");
        }
        if (command.requestId() == null || command.requestId().isBlank()) {
            throw new BusinessException(WalletErrorCode.INVALID_REQUEST, "requestId must not be blank");
        }
        if (command.txnType() == null) {
            throw new BusinessException(WalletErrorCode.INVALID_REQUEST, "txnType must not be null");
        }
        if (command.postings() == null || command.postings().size() < 2) {
            throw new BusinessException(WalletErrorCode.INVALID_REQUEST, "postings must contain at least two entries");
        }
    }

}
