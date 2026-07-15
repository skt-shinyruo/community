package com.nowcoder.community.wallet.domain.model;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.wallet.exception.WalletErrorCode;

import java.util.Date;
import java.util.Objects;
import java.util.UUID;

public class WalletAccount {

    private static final String OWNER_TYPE_USER = "USER";
    private static final String OWNER_TYPE_SYSTEM = "SYSTEM";
    private static final String ACCOUNT_TYPE_USER_WALLET = "USER_WALLET";
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_FROZEN = "FROZEN";
    private static final UUID SYSTEM_OWNER_ID = new UUID(0L, 0L);

    private UUID accountId;
    private String ownerType;
    private UUID ownerId;
    private String accountType;
    private long balance;
    private String status;
    private long version;
    private Date createTime;
    private Date updateTime;

    private WalletAccount(
            UUID accountId,
            String ownerType,
            UUID ownerId,
            String accountType,
            long balance,
            String status,
            long version,
            Date createTime,
            Date updateTime
    ) {
        this.accountId = Objects.requireNonNull(accountId, "accountId must not be null");
        this.ownerType = requireText(ownerType, "ownerType");
        this.ownerId = Objects.requireNonNull(ownerId, "ownerId must not be null");
        this.accountType = requireText(accountType, "accountType");
        if (balance < 0L) {
            throw new IllegalArgumentException("balance must not be negative");
        }
        this.balance = balance;
        this.status = requireStatus(status);
        if (version < 0L) {
            throw new IllegalArgumentException("version must not be negative");
        }
        this.version = version;
        this.createTime = copy(createTime);
        this.updateTime = copy(updateTime);
    }

    public static WalletAccount openUser(UUID accountId, UUID ownerId) {
        return new WalletAccount(
                accountId,
                OWNER_TYPE_USER,
                ownerId,
                ACCOUNT_TYPE_USER_WALLET,
                0L,
                STATUS_ACTIVE,
                0L,
                null,
                null
        );
    }

    public static WalletAccount openSystem(UUID accountId, String accountType) {
        return new WalletAccount(
                accountId,
                OWNER_TYPE_SYSTEM,
                SYSTEM_OWNER_ID,
                accountType,
                0L,
                STATUS_ACTIVE,
                0L,
                null,
                null
        );
    }

    public static WalletAccount reconstitute(
            UUID accountId,
            String ownerType,
            UUID ownerId,
            String accountType,
            long balance,
            String status,
            long version
    ) {
        return reconstitute(
                accountId,
                ownerType,
                ownerId,
                accountType,
                balance,
                status,
                version,
                null,
                null
        );
    }

    public static WalletAccount reconstitute(
            UUID accountId,
            String ownerType,
            UUID ownerId,
            String accountType,
            long balance,
            String status,
            long version,
            Date createTime,
            Date updateTime
    ) {
        return new WalletAccount(
                accountId,
                ownerType,
                ownerId,
                accountType,
                balance,
                status,
                version,
                createTime,
                updateTime
        );
    }

    public WalletAccountChange post(long delta) {
        if (delta < 0L && STATUS_FROZEN.equals(status)) {
            throw new BusinessException(
                    WalletErrorCode.ACCOUNT_FROZEN,
                    "frozen wallet account cannot post outgoing funds: accountId=" + accountId
            );
        }

        long nextBalance;
        try {
            nextBalance = Math.addExact(balance, delta);
        } catch (ArithmeticException exception) {
            throw new BusinessException(
                    WalletErrorCode.INVALID_REQUEST,
                    "wallet account balance overflow: accountId=" + accountId,
                    exception
            );
        }
        if (nextBalance < 0L) {
            throw new BusinessException(
                    WalletErrorCode.ACCOUNT_BALANCE_INSUFFICIENT,
                    "wallet account balance is insufficient: accountId=" + accountId
            );
        }
        return change(delta, nextBalance, status);
    }

    public WalletAccountChange freeze() {
        requireTransitionFrom(STATUS_ACTIVE, STATUS_FROZEN);
        return change(0L, balance, STATUS_FROZEN);
    }

    public WalletAccountChange unfreeze() {
        requireTransitionFrom(STATUS_FROZEN, STATUS_ACTIVE);
        return change(0L, balance, STATUS_ACTIVE);
    }

    public UUID getAccountId() {
        return accountId;
    }

    public String getOwnerType() {
        return ownerType;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public String getAccountType() {
        return accountType;
    }

    public long getBalance() {
        return balance;
    }

    public String getStatus() {
        return status;
    }

    public long getVersion() {
        return version;
    }

    public Date getCreateTime() {
        return copy(createTime);
    }

    public Date getUpdateTime() {
        return copy(updateTime);
    }

    private WalletAccountChange change(long delta, long nextBalance, String nextStatus) {
        long nextVersion;
        try {
            nextVersion = Math.addExact(version, 1L);
        } catch (ArithmeticException exception) {
            throw new BusinessException(
                    WalletErrorCode.ACCOUNT_UPDATE_CONFLICT,
                    "wallet account version cannot advance: accountId=" + accountId,
                    exception
            );
        }
        return new WalletAccountChange(accountId, version, delta, nextBalance, nextStatus, nextVersion);
    }

    private void requireTransitionFrom(String requiredStatus, String nextStatus) {
        if (!requiredStatus.equals(status)) {
            throw new BusinessException(
                    WalletErrorCode.ACCOUNT_UPDATE_CONFLICT,
                    "wallet account status transition is invalid: accountId=" + accountId
                            + ", currentStatus=" + status
                            + ", nextStatus=" + nextStatus
            );
        }
    }

    private static String requireStatus(String status) {
        if (!STATUS_ACTIVE.equals(status) && !STATUS_FROZEN.equals(status)) {
            throw new IllegalArgumentException("unsupported wallet account status: " + status);
        }
        return status;
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    private static Date copy(Date value) {
        return value == null ? null : new Date(value.getTime());
    }
}
