package com.nowcoder.community.wallet.application;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.wallet.domain.model.WalletAccount;
import com.nowcoder.community.wallet.domain.model.WalletAccountChange;
import com.nowcoder.community.wallet.domain.repository.WalletAccountRepository;
import com.nowcoder.community.wallet.domain.repository.WalletAccountRepository.ApplyResult;
import com.nowcoder.community.wallet.exception.WalletErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WalletAccountApplicationServiceOutcomeTest {

    private static final UUID ACCOUNT_ID = UUID.fromString("00000000-0000-7000-8000-000000000721");
    private static final UUID OWNER_ID = UUID.fromString("00000000-0000-7000-8000-000000000722");

    @Mock
    private WalletAccountRepository repository;

    @Test
    void appliedShouldReturnTheDomainCalculatedBalanceAndPersistItsVersionedChange() {
        WalletAccount account = account(500L, "ACTIVE", 7L);
        when(repository.apply(any(WalletAccountChange.class))).thenReturn(ApplyResult.APPLIED);

        long balance = service().apply(account, -120L);

        assertThat(balance).isEqualTo(380L);
        ArgumentCaptor<WalletAccountChange> changeCaptor = ArgumentCaptor.forClass(WalletAccountChange.class);
        verify(repository).apply(changeCaptor.capture());
        assertThat(changeCaptor.getValue().accountId()).isEqualTo(ACCOUNT_ID);
        assertThat(changeCaptor.getValue().expectedVersion()).isEqualTo(7L);
        assertThat(changeCaptor.getValue().nextBalance()).isEqualTo(380L);
        assertThat(changeCaptor.getValue().nextVersion()).isEqualTo(8L);
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("repositoryFailures")
    void repositoryFailureShouldMapToTheStableWalletError(
            ApplyResult repositoryResult,
            WalletErrorCode expectedError
    ) {
        WalletAccount account = account(500L, "ACTIVE", 7L);
        when(repository.apply(any(WalletAccountChange.class))).thenReturn(repositoryResult);

        assertThatThrownBy(() -> service().apply(account, -120L))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(expectedError));
    }

    @Test
    void setStatusShouldTranslateFreezeIntoADomainChange() {
        WalletAccount account = account(500L, "ACTIVE", 7L);
        when(repository.findByAccountId(ACCOUNT_ID)).thenReturn(account);
        when(repository.apply(any(WalletAccountChange.class))).thenReturn(ApplyResult.APPLIED);

        service().setStatus(ACCOUNT_ID, "FROZEN");

        ArgumentCaptor<WalletAccountChange> changeCaptor = ArgumentCaptor.forClass(WalletAccountChange.class);
        verify(repository).apply(changeCaptor.capture());
        assertThat(changeCaptor.getValue().expectedVersion()).isEqualTo(7L);
        assertThat(changeCaptor.getValue().delta()).isZero();
        assertThat(changeCaptor.getValue().nextBalance()).isEqualTo(500L);
        assertThat(changeCaptor.getValue().nextStatus()).isEqualTo("FROZEN");
        assertThat(changeCaptor.getValue().nextVersion()).isEqualTo(8L);
    }

    @Test
    void setStatusShouldTranslateUnfreezeIntoADomainChange() {
        WalletAccount account = account(500L, "FROZEN", 8L);
        when(repository.findByAccountId(ACCOUNT_ID)).thenReturn(account);
        when(repository.apply(any(WalletAccountChange.class))).thenReturn(ApplyResult.APPLIED);

        service().setStatus(ACCOUNT_ID, "ACTIVE");

        ArgumentCaptor<WalletAccountChange> changeCaptor = ArgumentCaptor.forClass(WalletAccountChange.class);
        verify(repository).apply(changeCaptor.capture());
        assertThat(changeCaptor.getValue().expectedVersion()).isEqualTo(8L);
        assertThat(changeCaptor.getValue().nextStatus()).isEqualTo("ACTIVE");
        assertThat(changeCaptor.getValue().nextVersion()).isEqualTo(9L);
    }

    private static Stream<Arguments> repositoryFailures() {
        return Stream.of(
                Arguments.of(ApplyResult.NOT_FOUND, WalletErrorCode.ACCOUNT_NOT_FOUND),
                Arguments.of(ApplyResult.VERSION_CONFLICT, WalletErrorCode.ACCOUNT_UPDATE_CONFLICT),
                Arguments.of(ApplyResult.INSUFFICIENT_FUNDS, WalletErrorCode.ACCOUNT_BALANCE_INSUFFICIENT)
        );
    }

    private WalletAccountApplicationService service() {
        return new WalletAccountApplicationService(repository);
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
