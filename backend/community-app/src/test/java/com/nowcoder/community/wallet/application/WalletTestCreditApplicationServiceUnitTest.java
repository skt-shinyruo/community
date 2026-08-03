package com.nowcoder.community.wallet.application;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.id.UuidV7Generator;
import com.nowcoder.community.common.idempotency.IdempotencyGuard;
import com.nowcoder.community.wallet.application.command.CreateRechargeCommand;
import com.nowcoder.community.wallet.application.command.CreateWithdrawCommand;
import com.nowcoder.community.wallet.application.result.RechargeOrderResult;
import com.nowcoder.community.wallet.application.result.WithdrawOrderResult;
import com.nowcoder.community.wallet.domain.model.RechargeOrder;
import com.nowcoder.community.wallet.domain.model.WithdrawOrder;
import com.nowcoder.community.wallet.domain.repository.CreationOutcome;
import com.nowcoder.community.wallet.domain.repository.RechargeOrderRepository;
import com.nowcoder.community.wallet.domain.repository.WithdrawOrderRepository;
import com.nowcoder.community.wallet.domain.service.WalletOrderDomainService;
import com.nowcoder.community.wallet.exception.WalletErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.UUID;
import java.util.function.Supplier;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WalletTestCreditApplicationServiceUnitTest {

    private static final UUID USER_ID = uuid(101);
    private static final long AMOUNT = 500L;

    @Test
    void grantInsertRaceShouldUseTheReloadedPaidOrderWithoutPostingAgain() {
        RechargeOrderRepository repository = mock(RechargeOrderRepository.class);
        WalletLedgerApplicationService ledgerService = mock(WalletLedgerApplicationService.class);
        WalletRechargeApplicationService service = rechargeService(repository, ledgerService);
        RechargeOrder persisted = rechargeOrder("grant-race", AMOUNT, "PAID");
        when(repository.findByUserIdAndRequestId(USER_ID, "grant-race")).thenReturn(null);
        when(repository.create(any(RechargeOrder.class)))
                .thenReturn(CreationOutcome.alreadyExists(persisted));

        RechargeOrderResult result = service.recharge(
                new CreateRechargeCommand(USER_ID, AMOUNT, "grant-race")
        );

        assertThat(result.orderId()).isEqualTo(persisted.getOrderId());
        assertThat(result.status()).isEqualTo("PAID");
        verify(ledgerService, never()).post(any());
    }

    @Test
    void grantInsertRaceShouldRejectReloadedAmountConflictWithoutPosting() {
        RechargeOrderRepository repository = mock(RechargeOrderRepository.class);
        WalletLedgerApplicationService ledgerService = mock(WalletLedgerApplicationService.class);
        WalletRechargeApplicationService service = rechargeService(repository, ledgerService);
        when(repository.findByUserIdAndRequestId(USER_ID, "grant-conflict")).thenReturn(null);
        when(repository.create(any(RechargeOrder.class)))
                .thenReturn(CreationOutcome.alreadyExists(
                        rechargeOrder("grant-conflict", AMOUNT + 1L, "CREATED")
                ));

        assertThatThrownBy(() -> service.recharge(
                new CreateRechargeCommand(USER_ID, AMOUNT, "grant-conflict")
        ))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(WalletErrorCode.REQUEST_REPLAY_CONFLICT));
        verify(ledgerService, never()).post(any());
    }

    @Test
    void grantShouldPropagateUnknownIntegrityViolationWithoutPosting() {
        RechargeOrderRepository repository = mock(RechargeOrderRepository.class);
        WalletLedgerApplicationService ledgerService = mock(WalletLedgerApplicationService.class);
        WalletRechargeApplicationService service = rechargeService(repository, ledgerService);
        DataIntegrityViolationException failure =
                new DataIntegrityViolationException("unknown recharge constraint");
        when(repository.findByUserIdAndRequestId(USER_ID, "grant-integrity")).thenReturn(null);
        when(repository.create(any(RechargeOrder.class))).thenThrow(failure);

        assertThatThrownBy(() -> service.recharge(
                new CreateRechargeCommand(USER_ID, AMOUNT, "grant-integrity")
        )).isSameAs(failure);
        verify(ledgerService, never()).post(any());
    }

    @Test
    void discardInsertRaceShouldUseTheReloadedSucceededOrderWithoutPostingAgain() {
        WithdrawOrderRepository repository = mock(WithdrawOrderRepository.class);
        WalletLedgerApplicationService ledgerService = mock(WalletLedgerApplicationService.class);
        WalletWithdrawApplicationService service = withdrawService(repository, ledgerService);
        WithdrawOrder persisted = withdrawOrder("discard-race", AMOUNT, "SUCCEEDED");
        when(repository.findByUserIdAndRequestId(USER_ID, "discard-race"))
                .thenReturn(null, null, persisted);
        when(repository.create(any(WithdrawOrder.class)))
                .thenReturn(CreationOutcome.alreadyExists(persisted));

        WithdrawOrderResult result = service.withdraw(
                new CreateWithdrawCommand(USER_ID, AMOUNT, "discard-race")
        );

        assertThat(result.orderId()).isEqualTo(persisted.getOrderId());
        assertThat(result.status()).isEqualTo("SUCCEEDED");
        verify(ledgerService, never()).post(any());
    }

    @Test
    void discardInsertRaceShouldRejectReloadedAmountConflictWithoutPosting() {
        WithdrawOrderRepository repository = mock(WithdrawOrderRepository.class);
        WalletLedgerApplicationService ledgerService = mock(WalletLedgerApplicationService.class);
        WalletWithdrawApplicationService service = withdrawService(repository, ledgerService);
        when(repository.findByUserIdAndRequestId(USER_ID, "discard-conflict")).thenReturn(null);
        when(repository.create(any(WithdrawOrder.class)))
                .thenReturn(CreationOutcome.alreadyExists(
                        withdrawOrder("discard-conflict", AMOUNT + 1L, "REQUESTED")
                ));

        assertThatThrownBy(() -> service.withdraw(
                new CreateWithdrawCommand(USER_ID, AMOUNT, "discard-conflict")
        ))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(WalletErrorCode.REQUEST_REPLAY_CONFLICT));
        verify(ledgerService, never()).post(any());
    }

    @Test
    void discardShouldPropagateUnknownIntegrityViolationWithoutPosting() {
        WithdrawOrderRepository repository = mock(WithdrawOrderRepository.class);
        WalletLedgerApplicationService ledgerService = mock(WalletLedgerApplicationService.class);
        WalletWithdrawApplicationService service = withdrawService(repository, ledgerService);
        DataIntegrityViolationException failure =
                new DataIntegrityViolationException("unknown withdraw constraint");
        when(repository.findByUserIdAndRequestId(USER_ID, "discard-integrity")).thenReturn(null);
        when(repository.create(any(WithdrawOrder.class))).thenThrow(failure);

        assertThatThrownBy(() -> service.withdraw(
                new CreateWithdrawCommand(USER_ID, AMOUNT, "discard-integrity")
        )).isSameAs(failure);
        verify(ledgerService, never()).post(any());
    }

    @Test
    void grantShouldRejectNullCommand() {
        WalletRechargeApplicationService service = rechargeService(
                mock(RechargeOrderRepository.class),
                mock(WalletLedgerApplicationService.class)
        );

        assertThatThrownBy(() -> service.recharge(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("command must not be null");
    }

    @Test
    void discardShouldRejectNullCommand() {
        WalletWithdrawApplicationService service = withdrawService(
                mock(WithdrawOrderRepository.class),
                mock(WalletLedgerApplicationService.class)
        );

        assertThatThrownBy(() -> service.withdraw(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("command must not be null");
    }

    private static WalletRechargeApplicationService rechargeService(
            RechargeOrderRepository repository,
            WalletLedgerApplicationService ledgerService
    ) {
        return new WalletRechargeApplicationService(
                repository,
                mock(WalletAccountApplicationService.class),
                ledgerService,
                passThroughIdempotencyGuard(),
                new WalletOrderDomainService(),
                new UuidV7Generator(),
                enabledPolicy(),
                acceptingQuota()
        );
    }

    private static WalletWithdrawApplicationService withdrawService(
            WithdrawOrderRepository repository,
            WalletLedgerApplicationService ledgerService
    ) {
        return new WalletWithdrawApplicationService(
                repository,
                mock(WalletAccountApplicationService.class),
                ledgerService,
                passThroughIdempotencyGuard(),
                new WalletOrderDomainService(),
                new UuidV7Generator(),
                enabledPolicy(),
                acceptingQuota()
        );
    }

    private static IdempotencyGuard passThroughIdempotencyGuard() {
        IdempotencyGuard guard = mock(IdempotencyGuard.class);
        doAnswer(invocation -> ((Supplier<?>) invocation.getArgument(6)).get())
                .when(guard)
                .executeRequired(anyString(), any(UUID.class), anyString(), anyString(), any(), any(), any());
        return guard;
    }

    private static WalletTestCreditPolicy enabledPolicy() {
        WalletTestCreditProperties properties = new WalletTestCreditProperties();
        properties.setEnabled(true);
        properties.setGrantEnabled(true);
        properties.setDiscardEnabled(true);
        properties.setMaxGrantPerRequest(1_000L);
        properties.setMaxDiscardPerRequest(1_000L);
        properties.setGrantQuotaPerUser(5_000L);
        properties.setDiscardQuotaPerUser(5_000L);
        return new WalletTestCreditPolicy(properties);
    }

    private static WalletTestCreditQuotaPort acceptingQuota() {
        WalletTestCreditQuotaPort quota = mock(WalletTestCreditQuotaPort.class);
        when(quota.tryReserveGrant(any(UUID.class), anyLong(), anyLong())).thenReturn(true);
        when(quota.tryReserveDiscard(any(UUID.class), anyLong(), anyLong())).thenReturn(true);
        return quota;
    }

    private static RechargeOrder rechargeOrder(String requestId, long amount, String status) {
        RechargeOrder order = new RechargeOrder();
        order.setOrderId(uuid(601));
        order.setRequestId(requestId);
        order.setUserId(USER_ID);
        order.setAmount(amount);
        order.setStatus(status);
        return order;
    }

    private static WithdrawOrder withdrawOrder(String requestId, long amount, String status) {
        WithdrawOrder order = new WithdrawOrder();
        order.setOrderId(uuid(602));
        order.setRequestId(requestId);
        order.setUserId(USER_ID);
        order.setAmount(amount);
        order.setStatus(status);
        return order;
    }
}
