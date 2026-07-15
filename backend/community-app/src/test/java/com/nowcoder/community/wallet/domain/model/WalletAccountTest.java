package com.nowcoder.community.wallet.domain.model;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.wallet.exception.WalletErrorCode;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WalletAccountTest {

    private static final UUID ACCOUNT_ID = UUID.fromString("00000000-0000-7000-8000-000000000701");
    private static final UUID OWNER_ID = UUID.fromString("00000000-0000-7000-8000-000000000702");

    @Test
    void openUserShouldCreateAnActiveZeroBalanceAccount() {
        WalletAccount account = WalletAccount.openUser(ACCOUNT_ID, OWNER_ID);

        assertThat(account.getAccountId()).isEqualTo(ACCOUNT_ID);
        assertThat(account.getOwnerType()).isEqualTo("USER");
        assertThat(account.getOwnerId()).isEqualTo(OWNER_ID);
        assertThat(account.getAccountType()).isEqualTo("USER_WALLET");
        assertThat(account.getBalance()).isZero();
        assertThat(account.getStatus()).isEqualTo("ACTIVE");
        assertThat(account.getVersion()).isZero();
    }

    @Test
    void openSystemShouldUseTheCanonicalSystemOwner() {
        WalletAccount account = WalletAccount.openSystem(ACCOUNT_ID, "PLATFORM_CASH");

        assertThat(account.getOwnerType()).isEqualTo("SYSTEM");
        assertThat(account.getOwnerId()).isEqualTo(new UUID(0L, 0L));
        assertThat(account.getAccountType()).isEqualTo("PLATFORM_CASH");
        assertThat(account.getBalance()).isZero();
        assertThat(account.getStatus()).isEqualTo("ACTIVE");
        assertThat(account.getVersion()).isZero();
    }

    @Test
    void postShouldAdvanceBalanceAndVersionWithoutLosingTheExpectedVersion() {
        WalletAccount account = account(500L, "ACTIVE", 7L);

        WalletAccountChange change = account.post(-120L);

        assertThat(change.accountId()).isEqualTo(ACCOUNT_ID);
        assertThat(change.expectedVersion()).isEqualTo(7L);
        assertThat(change.delta()).isEqualTo(-120L);
        assertThat(change.nextBalance()).isEqualTo(380L);
        assertThat(change.nextStatus()).isEqualTo("ACTIVE");
        assertThat(change.nextVersion()).isEqualTo(8L);
    }

    @Test
    void outgoingPostShouldNeverProduceANegativeBalance() {
        WalletAccount account = account(100L, "ACTIVE", 3L);

        assertThatThrownBy(() -> account.post(-101L))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(WalletErrorCode.ACCOUNT_BALANCE_INSUFFICIENT));
    }

    @Test
    void frozenAccountShouldRejectOutgoingPostButStillAcceptIncomingFunds() {
        WalletAccount account = account(500L, "FROZEN", 9L);

        assertThatThrownBy(() -> account.post(-1L))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(WalletErrorCode.ACCOUNT_FROZEN));

        WalletAccountChange incoming = account.post(80L);
        assertThat(incoming.nextBalance()).isEqualTo(580L);
        assertThat(incoming.nextStatus()).isEqualTo("FROZEN");
        assertThat(incoming.nextVersion()).isEqualTo(10L);
    }

    @Test
    void freezeAndUnfreezeShouldProduceVersionedStateTransitions() {
        WalletAccount active = account(500L, "ACTIVE", 11L);
        WalletAccount frozen = account(500L, "FROZEN", 12L);

        WalletAccountChange freeze = active.freeze();
        WalletAccountChange unfreeze = frozen.unfreeze();

        assertThat(freeze.expectedVersion()).isEqualTo(11L);
        assertThat(freeze.delta()).isZero();
        assertThat(freeze.nextBalance()).isEqualTo(500L);
        assertThat(freeze.nextStatus()).isEqualTo("FROZEN");
        assertThat(freeze.nextVersion()).isEqualTo(12L);
        assertThat(unfreeze.expectedVersion()).isEqualTo(12L);
        assertThat(unfreeze.delta()).isZero();
        assertThat(unfreeze.nextBalance()).isEqualTo(500L);
        assertThat(unfreeze.nextStatus()).isEqualTo("ACTIVE");
        assertThat(unfreeze.nextVersion()).isEqualTo(13L);
    }

    @Test
    void freezeAndUnfreezeShouldRejectInvalidSourceStates() {
        assertThatThrownBy(() -> account(500L, "FROZEN", 11L).freeze())
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> account(500L, "ACTIVE", 11L).unfreeze())
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void aggregateShouldNotExposePersistenceSetters() {
        assertThat(Arrays.stream(WalletAccount.class.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .map(Method::getName)
                .filter(name -> name.startsWith("set")))
                .isEmpty();
    }

    private WalletAccount account(long balance, String status, long version) {
        return WalletAccount.reconstitute(
                ACCOUNT_ID,
                "USER",
                OWNER_ID,
                "USER_WALLET",
                balance,
                status,
                version
        );
    }
}
