package com.nowcoder.community.wallet.application;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.wallet.domain.model.RechargeOrder;
import com.nowcoder.community.wallet.exception.WalletErrorCode;
import com.nowcoder.community.wallet.infrastructure.persistence.MyBatisRechargeOrderRepository;
import com.nowcoder.community.wallet.infrastructure.persistence.dataobject.RechargeOrderDataObject;
import com.nowcoder.community.wallet.infrastructure.persistence.mapper.RechargeOrderMapper;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WalletDuplicateReplaySemanticsTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-7000-8000-000000000101");

    @Test
    void duplicateInsertMustReloadAndValidateThePersistedAggregateFingerprint() {
        RechargeOrderMapper mapper = mock(RechargeOrderMapper.class);
        WalletAccountApplicationService accountService = mock(WalletAccountApplicationService.class);
        WalletLedgerApplicationService ledgerService = mock(WalletLedgerApplicationService.class);
        WalletRechargeApplicationService service = new WalletRechargeApplicationService(
                new MyBatisRechargeOrderRepository(mapper),
                accountService,
                ledgerService
        );
        String requestId = "recharge:duplicate-fingerprint";
        RechargeOrderDataObject conflicting = RechargeOrderDataObject.from(
                order(requestId, USER_ID, 1_300L)
        );
        when(mapper.selectByUserIdAndRequestId(USER_ID, requestId)).thenReturn(null, conflicting);
        when(mapper.insert(any(RechargeOrderDataObject.class)))
                .thenThrow(new DuplicateKeyException("duplicate recharge request"));

        assertThatThrownBy(() -> service.complete(requestId, USER_ID, 1_200L))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(WalletErrorCode.REQUEST_REPLAY_CONFLICT));
        verify(ledgerService, never()).post(any());
    }

    @Test
    void unknownIntegrityFailureMustPropagateWithoutAFalseReplaySuccess() {
        RechargeOrderMapper mapper = mock(RechargeOrderMapper.class);
        WalletAccountApplicationService accountService = mock(WalletAccountApplicationService.class);
        WalletLedgerApplicationService ledgerService = mock(WalletLedgerApplicationService.class);
        WalletRechargeApplicationService service = new WalletRechargeApplicationService(
                new MyBatisRechargeOrderRepository(mapper),
                accountService,
                ledgerService
        );
        String requestId = "recharge:unknown-integrity";
        DataIntegrityViolationException unknown = new DataIntegrityViolationException("unknown recharge constraint");
        when(mapper.selectByUserIdAndRequestId(USER_ID, requestId)).thenReturn(null);
        when(mapper.insert(any(RechargeOrderDataObject.class))).thenThrow(unknown);

        assertThatThrownBy(() -> service.complete(requestId, USER_ID, 1_200L))
                .isSameAs(unknown);
        verify(ledgerService, never()).post(any());
    }

    private static RechargeOrder order(String requestId, UUID userId, long amount) {
        RechargeOrder order = new RechargeOrder();
        order.setOrderId(UUID.fromString("00000000-0000-7000-8000-000000000601"));
        order.setRequestId(requestId);
        order.setUserId(userId);
        order.setAmount(amount);
        order.setStatus("CREATED");
        return order;
    }
}
