package com.nowcoder.community.wallet.domain.service;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.wallet.exception.WalletErrorCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WalletAccountDomainServiceTest {

    private final WalletAccountDomainService service = new WalletAccountDomainService();

    @Test
    void activeWalletRequiredShouldAcceptActiveWallet() {
        assertThatCode(() -> service.requireActive("ACTIVE"))
                .doesNotThrowAnyException();
    }

    @Test
    void activeWalletRequiredShouldRejectFrozenWallet() {
        assertThatThrownBy(() -> service.requireActive("FROZEN"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(WalletErrorCode.ACCOUNT_FROZEN));
    }

    @Test
    void normalDirectionShouldDriveDeltaSign() {
        assertThat(service.deltaOf("DEBIT", "DEBIT", 100)).isEqualTo(100);
        assertThat(service.deltaOf("DEBIT", "CREDIT", 100)).isEqualTo(-100);
        assertThat(service.deltaOf("CREDIT", "CREDIT", 100)).isEqualTo(100);
        assertThat(service.deltaOf("CREDIT", "DEBIT", 100)).isEqualTo(-100);
    }

    @Test
    void migrationHoldShouldStayUnsupported() {
        assertThatThrownBy(() -> service.validateSystemAccountType("MIGRATION_HOLD"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(WalletErrorCode.INVALID_REQUEST));
        assertThatThrownBy(() -> service.normalDirectionOf("MIGRATION_HOLD"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(WalletErrorCode.INVALID_REQUEST));
    }
}
