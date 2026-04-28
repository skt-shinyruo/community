package com.nowcoder.community.wallet.domain.service;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.wallet.exception.WalletErrorCode;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WalletOrderDomainServiceTest {

    private final WalletOrderDomainService service = new WalletOrderDomainService();

    @Test
    void validatePositiveAmountShouldRejectZeroAndNegative() {
        assertThatThrownBy(() -> service.validatePositiveAmount(0))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.validatePositiveAmount(-1))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void transferShouldRejectSameUser() {
        UUID userId = uuid(1);

        assertThatThrownBy(() -> service.validateTransfer(userId, userId, 100))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(WalletErrorCode.INVALID_TRANSFER));
    }

    @Test
    void transferShouldAcceptDifferentUsersWithPositiveAmount() {
        assertThatCode(() -> service.validateTransfer(uuid(1), uuid(2), 100))
                .doesNotThrowAnyException();
    }
}
