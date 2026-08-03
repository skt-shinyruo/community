package com.nowcoder.community.wallet.application;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.wallet.exception.WalletErrorCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WalletTestCreditPolicyTest {

    @Test
    void actionsShouldBeDisabledByDefault() {
        WalletTestCreditPolicy policy = new WalletTestCreditPolicy(new WalletTestCreditProperties());

        assertThatThrownBy(() -> policy.assertGrantAllowed(1L))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(WalletErrorCode.TEST_CREDITS_DISABLED));
        assertThatThrownBy(() -> policy.assertDiscardAllowed(1L))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(WalletErrorCode.TEST_CREDITS_DISABLED));
    }

    @Test
    void globalAndPerActionFlagsMustBothBeExplicitlyEnabled() {
        WalletTestCreditProperties properties = new WalletTestCreditProperties();
        properties.setEnabled(true);
        WalletTestCreditPolicy policy = new WalletTestCreditPolicy(properties);

        assertThatThrownBy(() -> policy.assertGrantAllowed(1L))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(WalletErrorCode.TEST_CREDITS_DISABLED));

        properties.setGrantEnabled(true);
        assertThatCode(() -> policy.assertGrantAllowed(1L)).doesNotThrowAnyException();
        assertThatThrownBy(() -> policy.assertDiscardAllowed(1L))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void perRequestLimitShouldNeverExceedTotalQuota() {
        WalletTestCreditProperties properties = new WalletTestCreditProperties();
        properties.setEnabled(true);
        properties.setGrantEnabled(true);
        properties.setMaxGrantPerRequest(1000L);
        properties.setGrantQuotaPerUser(600L);
        WalletTestCreditPolicy policy = new WalletTestCreditPolicy(properties);

        assertThat(properties.getMaxGrantPerRequest()).isEqualTo(600L);
        assertThatThrownBy(() -> policy.assertGrantAllowed(601L))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(WalletErrorCode.TEST_CREDIT_LIMIT_EXCEEDED));
    }
}
