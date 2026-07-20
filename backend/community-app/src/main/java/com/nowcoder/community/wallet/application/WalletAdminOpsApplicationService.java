package com.nowcoder.community.wallet.application;

import com.nowcoder.community.common.id.UuidV7Generator;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.wallet.domain.model.WalletAdminAction;
import com.nowcoder.community.wallet.domain.model.WalletEntry;
import com.nowcoder.community.wallet.domain.model.WalletPosting;
import com.nowcoder.community.wallet.domain.model.WalletTxn;
import com.nowcoder.community.wallet.domain.model.WalletTxnType;
import com.nowcoder.community.wallet.domain.repository.CreationOutcome;
import com.nowcoder.community.wallet.domain.repository.WalletAdminActionRepository;
import com.nowcoder.community.wallet.domain.repository.WalletLedgerRepository;
import com.nowcoder.community.wallet.domain.service.WalletAccountDomainService;
import com.nowcoder.community.wallet.domain.service.WalletAdminDomainService;
import com.nowcoder.community.wallet.exception.WalletErrorCode;
import com.nowcoder.community.user.api.query.UserLookupQueryApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import static com.nowcoder.community.common.exception.CommonErrorCode.NOT_FOUND;

@Service
public class WalletAdminOpsApplicationService {

    private static final String ACTION_FREEZE_WALLET = "FREEZE_WALLET";
    private static final String ACTION_REVERSE_TXN = "REVERSE_TXN";
    private static final Set<String> REVERSIBLE_TXN_TYPES = Set.of(
            WalletTxnType.TRANSFER.name(),
            WalletTxnType.REWARD_ISSUE.name()
    );

    private final WalletAccountApplicationService accountService;
    private final WalletLedgerApplicationService ledgerService;
    private final WalletLedgerRepository walletLedgerRepository;
    private final WalletAdminActionRepository walletAdminActionRepository;
    private final WalletAdminDomainService adminDomainService;
    private final UuidV7Generator idGenerator;
    private final UserLookupQueryApi userLookupQueryApi;

    @Autowired
    public WalletAdminOpsApplicationService(WalletAccountApplicationService accountService,
                                            WalletLedgerApplicationService ledgerService,
                                            WalletLedgerRepository walletLedgerRepository,
                                            WalletAdminActionRepository walletAdminActionRepository,
                                            UserLookupQueryApi userLookupQueryApi) {
        this(
                accountService,
                ledgerService,
                walletLedgerRepository,
                walletAdminActionRepository,
                new WalletAdminDomainService(),
                new UuidV7Generator(),
                userLookupQueryApi
        );
    }

    WalletAdminOpsApplicationService(WalletAccountApplicationService accountService,
                                     WalletLedgerApplicationService ledgerService,
                                     WalletLedgerRepository walletLedgerRepository,
                                     WalletAdminActionRepository walletAdminActionRepository,
                                     WalletAdminDomainService adminDomainService,
                                     UuidV7Generator idGenerator,
                                     UserLookupQueryApi userLookupQueryApi) {
        this.accountService = accountService;
        this.ledgerService = ledgerService;
        this.walletLedgerRepository = walletLedgerRepository;
        this.walletAdminActionRepository = walletAdminActionRepository;
        this.adminDomainService = adminDomainService;
        this.idGenerator = idGenerator;
        this.userLookupQueryApi = userLookupQueryApi;
    }

    @Transactional
    public void freezeWallet(UUID actorUserId, UUID targetUserId, String reason) {
        String normalizedReason = adminDomainService.validateAdminAction(actorUserId, reason);
        validateTargetUser(targetUserId);

        var account = accountService.loadUserWallet(targetUserId);
        accountService.setStatus(account.getAccountId(), WalletAccountDomainService.STATUS_FROZEN);
        insertAudit("wallet-admin:freeze:" + UUID.randomUUID(), actorUserId, account.getAccountId(), ACTION_FREEZE_WALLET, 0L, normalizedReason);
    }

    @Transactional
    public void reverseTxn(UUID actorUserId, String txnRef, String reason) {
        String normalizedReason = adminDomainService.validateAdminAction(actorUserId, reason);
        if (txnRef == null || txnRef.isBlank()) {
            throw new BusinessException(WalletErrorCode.INVALID_REQUEST, "txnRef must not be blank");
        }

        WalletTxn txn = requireTxn(txnRef);
        if (!REVERSIBLE_TXN_TYPES.contains(txn.getTxnType())) {
            throw new BusinessException(WalletErrorCode.INVALID_REQUEST, "wallet txn is not reversible: txnRef=" + txnRef);
        }

        List<WalletEntry> entries = walletLedgerRepository.findEntriesByTxnId(txn.getTxnId());
        if (entries.isEmpty()) {
            throw new BusinessException(WalletErrorCode.INVALID_REQUEST, "wallet txn has no entries: txnRef=" + txnRef);
        }

        String reversalRequestId = reversalRequestId(txn.getRequestId());
        List<WalletPosting> reversal = entries.stream()
                .map(this::reverseOf)
                .toList();
        ledgerService.postPrivilegedCorrection(reversalRequestId, WalletTxnType.REVERSAL, reversal);
        insertReverseAuditIfAbsent(actorUserId, txn, normalizedReason);
    }

    private WalletTxn requireTxn(String txnRef) {
        WalletTxn txn = walletLedgerRepository.findTxnByRequestId(txnRef);
        if (txn != null) {
            return txn;
        }
        throw new BusinessException(WalletErrorCode.INVALID_REQUEST, "wallet txn not found: txnRef=" + txnRef);
    }

    private WalletPosting reverseOf(WalletEntry entry) {
        if ("DEBIT".equals(entry.getDirection())) {
            return WalletPosting.credit(entry.getAccountId(), entry.getAmount());
        }
        return WalletPosting.debit(entry.getAccountId(), entry.getAmount());
    }

    private void insertReverseAuditIfAbsent(UUID actorUserId, WalletTxn txn, String reason) {
        String requestId = reverseAuditRequestId(txn.getRequestId());
        insertAudit(requestId, actorUserId, txn.getTxnId(), ACTION_REVERSE_TXN, txn.getAmount(), reason);
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
        CreationOutcome<WalletAdminAction> outcome = walletAdminActionRepository.create(action);
        if (outcome == null
                || outcome.status() == CreationOutcome.Status.CONFLICT
                || outcome.aggregate() == null) {
            throw new BusinessException(
                    WalletErrorCode.REQUEST_REPLAY_CONFLICT,
                    "wallet admin action creation conflict: requestId=" + requestId
            );
        }
        WalletAdminAction persisted = outcome.aggregate();
        if (!Objects.equals(requestId, persisted.getRequestId())
                || !Objects.equals(actorUserId, persisted.getActorUserId())
                || !Objects.equals(targetAccountId, persisted.getTargetAccountId())
                || !Objects.equals(actionType, persisted.getActionType())
                || amount != persisted.getAmount()
                || !Objects.equals(reason, persisted.getRemark())) {
            throw new BusinessException(
                    WalletErrorCode.REQUEST_REPLAY_CONFLICT,
                    "wallet admin action replay conflict: requestId=" + requestId
            );
        }
    }

    private String reverseAuditRequestId(String txnRef) {
        return "wallet-admin:reverse:" + txnRef;
    }

    private String reversalRequestId(String txnRef) {
        return "reversal:" + txnRef;
    }

    private void validateTargetUser(UUID targetUserId) {
        if (targetUserId == null) {
            throw new BusinessException(WalletErrorCode.INVALID_REQUEST, "targetUserId must not be null");
        }
        if (userLookupQueryApi.getSummaryById(targetUserId) == null) {
            throw new BusinessException(NOT_FOUND, "wallet target user not found: userId=" + targetUserId);
        }
    }
}
