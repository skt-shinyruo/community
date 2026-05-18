package com.nowcoder.community.wallet.application;

import com.nowcoder.community.common.id.UuidV7Generator;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.wallet.domain.model.WalletAccount;
import com.nowcoder.community.wallet.domain.model.WalletPosting;
import com.nowcoder.community.wallet.domain.repository.WalletAccountRepository;
import com.nowcoder.community.wallet.domain.service.WalletAccountDomainService;
import com.nowcoder.community.wallet.exception.WalletErrorCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

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
        account.setStatus(nextStatus);
        apply(account, 0L);
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
        int updated = walletAccountRepository.updateBalanceWithVersion(
                account.getAccountId(),
                account.getVersion(),
                delta,
                account.getStatus()
        );
        if (updated != 1) {
            throw new BusinessException(
                    classifyUpdateFailure(account, delta),
                    "wallet account update failed: accountId=" + account.getAccountId()
            );
        }

        WalletAccount latest = walletAccountRepository.findByAccountId(account.getAccountId());
        if (latest == null) {
            throw new BusinessException(WalletErrorCode.ACCOUNT_NOT_FOUND, "wallet account not found: accountId=" + account.getAccountId());
        }
        return latest.getBalance();
    }

    private WalletAccount ensureAccount(String ownerType, UUID ownerId, String accountType) {
        WalletAccount existing = walletAccountRepository.findByOwner(ownerType, ownerId, accountType);
        if (existing != null) {
            return existing;
        }

        WalletAccount account = new WalletAccount();
        account.setAccountId(idGenerator.next());
        account.setOwnerType(ownerType);
        account.setOwnerId(ownerId);
        account.setAccountType(accountType);
        account.setBalance(0L);
        account.setStatus(WalletAccountDomainService.STATUS_ACTIVE);
        account.setVersion(0L);
        try {
            walletAccountRepository.insert(account);
            return account;
        } catch (DataIntegrityViolationException ignored) {
            WalletAccount duplicated = walletAccountRepository.findByOwner(ownerType, ownerId, accountType);
            if (duplicated != null) {
                return duplicated;
            }
            throw ignored;
        }
    }

    private WalletErrorCode classifyUpdateFailure(WalletAccount beforeAccount, long delta) {
        WalletAccount latestAccount = walletAccountRepository.findByAccountId(beforeAccount.getAccountId());
        if (latestAccount == null) {
            return WalletErrorCode.ACCOUNT_NOT_FOUND;
        }
        if (latestAccount.getVersion() != beforeAccount.getVersion()) {
            return WalletErrorCode.ACCOUNT_UPDATE_CONFLICT;
        }
        if (delta < 0 && latestAccount.getBalance() + delta < 0) {
            return WalletErrorCode.ACCOUNT_BALANCE_INSUFFICIENT;
        }
        return WalletErrorCode.ACCOUNT_UPDATE_CONFLICT;
    }

    private UUID requireUserId(UUID userId) {
        if (userId == null) {
            throw new BusinessException(WalletErrorCode.INVALID_REQUEST, "userId must not be null");
        }
        return userId;
    }
}
