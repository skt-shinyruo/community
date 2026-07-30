package com.nowcoder.community.wallet.application;

import com.nowcoder.community.common.id.UuidV7Generator;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.wallet.application.result.WalletSummaryResult;
import com.nowcoder.community.wallet.domain.model.WalletAccount;
import com.nowcoder.community.wallet.domain.model.WalletAccountChange;
import com.nowcoder.community.wallet.domain.model.WalletPosting;
import com.nowcoder.community.wallet.domain.model.WalletPostingPolicy;
import com.nowcoder.community.wallet.domain.repository.CreationOutcome;
import com.nowcoder.community.wallet.domain.repository.WalletAccountRepository;
import com.nowcoder.community.wallet.domain.repository.WalletAccountRepository.ApplyResult;
import com.nowcoder.community.wallet.domain.service.WalletAccountDomainService;
import com.nowcoder.community.wallet.exception.WalletErrorCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.UUID;

@Service
public class WalletAccountApplicationService {

    private static final UUID SYSTEM_OWNER_ID = new UUID(0L, 0L);

    private final WalletAccountRepository walletAccountRepository;
    private final WalletAccountDomainService domainService;
    private final UuidV7Generator idGenerator;

    @Autowired
    public WalletAccountApplicationService(WalletAccountRepository walletAccountRepository) {
        this(walletAccountRepository, new WalletAccountDomainService(), new UuidV7Generator());
    }

    WalletAccountApplicationService(WalletAccountRepository walletAccountRepository,
                                    WalletAccountDomainService domainService,
                                    UuidV7Generator idGenerator) {
        this.walletAccountRepository = walletAccountRepository;
        this.domainService = domainService;
        this.idGenerator = idGenerator;
    }

    public UUID ensureUserWallet(UUID userId) {
        return ensureAccount(
                WalletAccountDomainService.OWNER_TYPE_USER,
                requireUserId(userId),
                WalletAccountDomainService.ACCOUNT_TYPE_USER_WALLET
        ).getAccountId();
    }

    public UUID ensureSystemAccount(String accountType) {
        domainService.validateSystemAccountType(accountType);
        return ensureAccount(WalletAccountDomainService.OWNER_TYPE_SYSTEM, SYSTEM_OWNER_ID, accountType).getAccountId();
    }

    public WalletAccount findUserWallet(UUID userId) {
        return walletAccountRepository.findByOwner(
                WalletAccountDomainService.OWNER_TYPE_USER,
                requireUserId(userId),
                WalletAccountDomainService.ACCOUNT_TYPE_USER_WALLET
        );
    }

    public long balanceOfUser(UUID userId) {
        WalletAccount account = walletAccountRepository.findByOwner(
                WalletAccountDomainService.OWNER_TYPE_USER,
                requireUserId(userId),
                WalletAccountDomainService.ACCOUNT_TYPE_USER_WALLET
        );
        return account == null ? 0L : account.getBalance();
    }

    public WalletSummaryResult summary(UUID userId) {
        return new WalletSummaryResult(userId, balanceOfUser(userId), statusOfUser(userId));
    }

    public String statusOfUser(UUID userId) {
        WalletAccount account = walletAccountRepository.findByOwner(
                WalletAccountDomainService.OWNER_TYPE_USER,
                requireUserId(userId),
                WalletAccountDomainService.ACCOUNT_TYPE_USER_WALLET
        );
        return account == null ? WalletAccountDomainService.STATUS_UNKNOWN : account.getStatus();
    }

    public long balanceOfSystem(String accountType) {
        domainService.validateSystemAccountType(accountType);
        WalletAccount account = walletAccountRepository.findByOwner(WalletAccountDomainService.OWNER_TYPE_SYSTEM, SYSTEM_OWNER_ID, accountType);
        return account == null ? 0L : account.getBalance();
    }

    public WalletAccount loadUserWallet(UUID userId) {
        return ensureAccount(
                WalletAccountDomainService.OWNER_TYPE_USER,
                requireUserId(userId),
                WalletAccountDomainService.ACCOUNT_TYPE_USER_WALLET
        );
    }

    public void requireUserWalletActive(UUID userId) {
        WalletAccount account = loadUserWallet(userId);
        domainService.requireActive(account.getStatus());
    }

    public void setStatus(UUID accountId, String nextStatus) {
        domainService.validateUserAccountStatus(nextStatus);
        WalletAccount account = lock(accountId);
        WalletAccountChange change = WalletAccountDomainService.STATUS_FROZEN.equals(nextStatus)
                ? account.freeze()
                : account.unfreeze();
        apply(change);
    }

    public WalletAccount lock(UUID accountId) {
        WalletAccount account = walletAccountRepository.findByAccountId(accountId);
        if (account == null) {
            throw new BusinessException(WalletErrorCode.ACCOUNT_NOT_FOUND, "wallet account not found: accountId=" + accountId);
        }
        return account;
    }

    public long deltaOf(WalletAccount account, WalletPosting posting) {
        return domainService.deltaOf(account.getAccountType(), posting);
    }

    public long apply(WalletAccount account, long delta) {
        return apply(account, delta, WalletPostingPolicy.NORMAL);
    }

    long apply(WalletAccount account, long delta, WalletPostingPolicy policy) {
        return apply(account.post(delta, policy));
    }

    private WalletAccount ensureAccount(String ownerType, UUID ownerId, String accountType) {
        WalletAccount existing = walletAccountRepository.findByOwner(ownerType, ownerId, accountType);
        if (existing != null) {
            return existing;
        }

        WalletAccount account = WalletAccountDomainService.OWNER_TYPE_USER.equals(ownerType)
                ? WalletAccount.openUser(idGenerator.next(), ownerId)
                : WalletAccount.openSystem(idGenerator.next(), accountType);
        CreationOutcome<WalletAccount> outcome = walletAccountRepository.create(account);
        if (outcome == null
                || outcome.status() == CreationOutcome.Status.CONFLICT
                || outcome.aggregate() == null) {
            throw new BusinessException(
                    WalletErrorCode.ACCOUNT_UPDATE_CONFLICT,
                    "wallet account creation conflict: ownerId=" + ownerId + ", accountType=" + accountType
            );
        }
        WalletAccount persisted = outcome.aggregate();
        if (!Objects.equals(ownerType, persisted.getOwnerType())
                || !Objects.equals(ownerId, persisted.getOwnerId())
                || !Objects.equals(accountType, persisted.getAccountType())) {
            throw new BusinessException(
                    WalletErrorCode.ACCOUNT_UPDATE_CONFLICT,
                    "wallet account replay conflict: ownerId=" + ownerId + ", accountType=" + accountType
            );
        }
        return persisted;
    }

    private long apply(WalletAccountChange change) {
        ApplyResult result = walletAccountRepository.apply(change);
        if (result == ApplyResult.APPLIED) {
            return change.nextBalance();
        }
        if (result == null) {
            throw updateFailed(change, WalletErrorCode.ACCOUNT_UPDATE_CONFLICT);
        }
        WalletErrorCode errorCode = switch (result) {
            case NOT_FOUND -> WalletErrorCode.ACCOUNT_NOT_FOUND;
            case INSUFFICIENT_FUNDS -> WalletErrorCode.ACCOUNT_BALANCE_INSUFFICIENT;
            case VERSION_CONFLICT -> WalletErrorCode.ACCOUNT_UPDATE_CONFLICT;
            case APPLIED -> throw new IllegalStateException("handled above");
        };
        throw updateFailed(change, errorCode);
    }

    private BusinessException updateFailed(WalletAccountChange change, WalletErrorCode errorCode) {
        return new BusinessException(errorCode, "wallet account update failed: accountId=" + change.accountId());
    }

    private UUID requireUserId(UUID userId) {
        if (userId == null) {
            throw new BusinessException(WalletErrorCode.INVALID_REQUEST, "userId must not be null");
        }
        return userId;
    }
}
