package com.nowcoder.community.wallet.service;

import com.nowcoder.community.common.id.UuidV7Generator;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.wallet.entity.WalletAdminAction;
import com.nowcoder.community.wallet.entity.WalletEntry;
import com.nowcoder.community.wallet.entity.WalletTxn;
import com.nowcoder.community.wallet.exception.WalletErrorCode;
import com.nowcoder.community.wallet.mapper.WalletAdminActionMapper;
import com.nowcoder.community.wallet.mapper.WalletEntryMapper;
import com.nowcoder.community.wallet.mapper.WalletTxnMapper;
import com.nowcoder.community.wallet.model.WalletPosting;
import com.nowcoder.community.wallet.model.WalletTxnType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class AdminWalletOpsService {

    private static final String STATUS_FROZEN = "FROZEN";
    private static final String ACTION_FREEZE_WALLET = "FREEZE_WALLET";
    private static final String ACTION_REVERSE_TXN = "REVERSE_TXN";
    private static final Set<String> REVERSIBLE_TXN_TYPES = Set.of(
            WalletTxnType.TRANSFER.name(),
            WalletTxnType.ORDER_RELEASE.name(),
            WalletTxnType.REWARD_ISSUE.name()
    );

    private final WalletAccountService accountService;
    private final WalletLedgerService ledgerService;
    private final WalletTxnMapper walletTxnMapper;
    private final WalletEntryMapper walletEntryMapper;
    private final WalletAdminActionMapper walletAdminActionMapper;
    private final UuidV7Generator idGenerator;

    @Autowired
    public AdminWalletOpsService(WalletAccountService accountService,
                                 WalletLedgerService ledgerService,
                                 WalletTxnMapper walletTxnMapper,
                                 WalletEntryMapper walletEntryMapper,
                                 WalletAdminActionMapper walletAdminActionMapper) {
        this(
                accountService,
                ledgerService,
                walletTxnMapper,
                walletEntryMapper,
                walletAdminActionMapper,
                new UuidV7Generator()
        );
    }

    AdminWalletOpsService(WalletAccountService accountService,
                          WalletLedgerService ledgerService,
                          WalletTxnMapper walletTxnMapper,
                          WalletEntryMapper walletEntryMapper,
                          WalletAdminActionMapper walletAdminActionMapper,
                          UuidV7Generator idGenerator) {
        this.accountService = accountService;
        this.ledgerService = ledgerService;
        this.walletTxnMapper = walletTxnMapper;
        this.walletEntryMapper = walletEntryMapper;
        this.walletAdminActionMapper = walletAdminActionMapper;
        this.idGenerator = idGenerator;
    }

    @Transactional
    public void freezeWallet(UUID actorUserId, UUID targetUserId, String reason) {
        validateActor(actorUserId);
        validateTargetUser(targetUserId);
        String normalizedReason = requireReason(reason);

        var account = accountService.loadUserWallet(targetUserId);
        accountService.setStatus(account.getAccountId(), STATUS_FROZEN);
        insertAudit("wallet-admin:freeze:" + UUID.randomUUID(), actorUserId, account.getAccountId(), ACTION_FREEZE_WALLET, 0L, normalizedReason);
    }

    @Transactional
    public void reverseTxn(UUID actorUserId, String txnRef, String reason) {
        validateActor(actorUserId);
        String normalizedReason = requireReason(reason);
        if (!StringUtils.hasText(txnRef)) {
            throw new BusinessException(WalletErrorCode.INVALID_REQUEST, "txnRef must not be blank");
        }

        WalletTxn txn = walletTxnMapper.selectByRequestId(txnRef);
        if (txn == null) {
            throw new BusinessException(WalletErrorCode.INVALID_REQUEST, "wallet txn not found: txnRef=" + txnRef);
        }
        if (!REVERSIBLE_TXN_TYPES.contains(txn.getTxnType())) {
            throw new BusinessException(WalletErrorCode.INVALID_REQUEST, "wallet txn is not reversible: txnRef=" + txnRef);
        }

        List<WalletEntry> entries = walletEntryMapper.selectByTxnId(txn.getTxnId());
        if (entries.isEmpty()) {
            throw new BusinessException(WalletErrorCode.INVALID_REQUEST, "wallet txn has no entries: txnRef=" + txnRef);
        }

        String reversalRequestId = reversalRequestId(txnRef);
        if (walletTxnMapper.selectByRequestId(reversalRequestId) != null) {
            insertReverseAuditIfAbsent(actorUserId, txn, normalizedReason);
            return;
        }

        List<WalletPosting> reversal = entries.stream()
                .map(this::reverseOf)
                .toList();
        ensureReversalCanBeApplied(txnRef, reversal);
        ledgerService.post(reversalRequestId, WalletTxnType.REVERSAL, reversal);
        insertReverseAuditIfAbsent(actorUserId, txn, normalizedReason);
    }

    private WalletPosting reverseOf(WalletEntry entry) {
        if ("DEBIT".equals(entry.getDirection())) {
            return WalletPosting.credit(entry.getAccountId(), entry.getAmount());
        }
        return WalletPosting.debit(entry.getAccountId(), entry.getAmount());
    }

    private void ensureReversalCanBeApplied(String txnRef, List<WalletPosting> reversal) {
        for (WalletPosting posting : reversal) {
            var account = accountService.lock(posting.accountId());
            long delta = accountService.deltaOf(account, posting);
            if (delta < 0 && account.getBalance() + delta < 0) {
                throw new BusinessException(
                        WalletErrorCode.ACCOUNT_BALANCE_INSUFFICIENT,
                        "reversal rejected: txnRef=" + txnRef + ", accountId=" + account.getAccountId() + ", availableBalance=" + account.getBalance()
                );
            }
        }
    }

    private void insertReverseAuditIfAbsent(UUID actorUserId, WalletTxn txn, String reason) {
        String requestId = reverseAuditRequestId(txn.getRequestId());
        if (walletAdminActionMapper.selectByRequestId(requestId) != null) {
            return;
        }
        try {
            insertAudit(requestId, actorUserId, txn.getTxnId(), ACTION_REVERSE_TXN, txn.getAmount(), reason);
        } catch (DataIntegrityViolationException ex) {
            if (walletAdminActionMapper.selectByRequestId(requestId) != null) {
                return;
            }
            throw ex;
        }
    }

    private void insertAudit(String requestId, UUID actorUserId, UUID targetAccountId, String actionType, long amount, String reason) {
        WalletAdminAction action = new WalletAdminAction();
        action.setActionId(idGenerator.next());
        action.setRequestId(requestId);
        action.setActorUserId(actorUserId);
        action.setTargetAccountId(targetAccountId);
        action.setActionType(actionType);
        action.setAmount(amount);
        action.setRemark(reason);
        walletAdminActionMapper.insert(action);
    }

    private String reverseAuditRequestId(String txnRef) {
        return "wallet-admin:reverse:" + txnRef;
    }

    private String reversalRequestId(String txnRef) {
        return "reversal:" + txnRef;
    }

    private void validateActor(UUID actorUserId) {
        if (actorUserId == null) {
            throw new BusinessException(WalletErrorCode.INVALID_REQUEST, "actorUserId must not be null");
        }
    }

    private void validateTargetUser(UUID targetUserId) {
        if (targetUserId == null) {
            throw new BusinessException(WalletErrorCode.INVALID_REQUEST, "targetUserId must not be null");
        }
    }

    private String requireReason(String reason) {
        String normalized = reason == null ? "" : reason.trim();
        if (!StringUtils.hasText(normalized)) {
            throw new BusinessException(WalletErrorCode.INVALID_REQUEST, "reason must not be blank");
        }
        return normalized;
    }
}
