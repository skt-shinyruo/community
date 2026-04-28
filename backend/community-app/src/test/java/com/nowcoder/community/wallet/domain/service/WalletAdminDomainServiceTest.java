package com.nowcoder.community.wallet.domain.service;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.wallet.exception.WalletErrorCode;
import org.junit.jupiter.api.Test;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WalletAdminDomainServiceTest {

    private final WalletAdminDomainService service = new WalletAdminDomainService();

    @Test
    void validateAdminActionShouldRejectMissingActor() {
        assertThatThrownBy(() -> service.validateAdminAction(null, "reason"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(WalletErrorCode.INVALID_REQUEST));
    }

    @Test
    void validateAdminActionShouldRejectBlankReason() {
        assertThatThrownBy(() -> service.validateAdminAction(uuid(1), " "))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(WalletErrorCode.INVALID_REQUEST));
    }

    @Test
    void validateAdminActionShouldAcceptActorAndReason() {
        assertThatCode(() -> service.validateAdminAction(uuid(1), "manual review"))
                .doesNotThrowAnyException();
    }
}
