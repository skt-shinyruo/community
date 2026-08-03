package com.nowcoder.community.wallet.application;

import com.nowcoder.community.common.id.UuidV7Generator;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.idempotency.IdempotencyGuard;
import com.nowcoder.community.common.idempotency.EffectiveIdempotencyKey;
import com.nowcoder.community.common.idempotency.IdempotencyKeyResolver;
import com.nowcoder.community.common.idempotency.RequestFingerprint;
import com.nowcoder.community.wallet.application.command.CreateWithdrawCommand;
import com.nowcoder.community.wallet.application.result.WithdrawOrderResult;
import com.nowcoder.community.wallet.domain.model.WithdrawOrder;
import com.nowcoder.community.wallet.domain.model.WalletLedgerCommand;
import com.nowcoder.community.wallet.domain.model.WalletPosting;
import com.nowcoder.community.wallet.domain.model.WalletTxnType;
import com.nowcoder.community.wallet.domain.repository.CreationOutcome;
import com.nowcoder.community.wallet.domain.repository.WithdrawOrderRepository;
import com.nowcoder.community.wallet.domain.service.WalletOrderDomainService;
import com.nowcoder.community.wallet.exception.WalletErrorCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class WalletWithdrawApplicationService {

    private static final String TEST_CREDIT_EXPENSE_ACCOUNT = "PLATFORM_TEST_CREDIT_EXPENSE";

    private final WithdrawOrderRepository withdrawOrderRepository;
    private final WalletAccountApplicationService accountService;
    private final WalletLedgerApplicationService ledgerService;
    private final IdempotencyGuard idempotencyGuard;
    private final WalletOrderDomainService orderDomainService;
    private final UuidV7Generator idGenerator;
    private final WalletTestCreditPolicy testCreditPolicy;
    private final WalletTestCreditQuotaPort testCreditQuotaPort;

    @Autowired
    public WalletWithdrawApplicationService(WithdrawOrderRepository withdrawOrderRepository,
                                            WalletAccountApplicationService accountService,
                                            WalletLedgerApplicationService ledgerService,
                                            IdempotencyGuard idempotencyGuard,
                                            WalletTestCreditPolicy testCreditPolicy,
                                            WalletTestCreditQuotaPort testCreditQuotaPort) {
        this(
                withdrawOrderRepository,
                accountService,
                ledgerService,
                idempotencyGuard,
                new WalletOrderDomainService(),
                new UuidV7Generator(),
                testCreditPolicy,
                testCreditQuotaPort
        );
    }

    WalletWithdrawApplicationService(WithdrawOrderRepository withdrawOrderRepository,
                                     WalletAccountApplicationService accountService,
                                     WalletLedgerApplicationService ledgerService,
                                     IdempotencyGuard idempotencyGuard,
                                     WalletOrderDomainService orderDomainService,
                                     UuidV7Generator idGenerator,
                                     WalletTestCreditPolicy testCreditPolicy,
                                     WalletTestCreditQuotaPort testCreditQuotaPort) {
        this.withdrawOrderRepository = withdrawOrderRepository;
        this.accountService = accountService;
        this.ledgerService = ledgerService;
        this.idempotencyGuard = idempotencyGuard;
        this.orderDomainService = orderDomainService;
        this.idGenerator = idGenerator;
        this.testCreditPolicy = testCreditPolicy;
        this.testCreditQuotaPort = testCreditQuotaPort;
    }

    @Transactional
    public WithdrawOrderResult withdraw(CreateWithdrawCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        orderDomainService.validatePositiveAmount(command.amount());
        testCreditPolicy.assertDiscardAllowed(command.amount());
        EffectiveIdempotencyKey effective = IdempotencyKeyResolver.resolve(command.idempotencyKey());
        return idempotencyGuard.executeRequired(
                "wallet:withdraw",
                command.userId(),
                effective.value(),
                RequestFingerprint.sha256("wallet:withdraw|amount=" + command.amount()),
                WalletErrorCode.REQUEST_REPLAY_CONFLICT,
                WithdrawOrderResult.class,
                () -> discardTestCredits(effective.value(), command.userId(), command.amount())
        );
    }

    private WithdrawOrderResult discardTestCreditsInternal(String requestId, UUID userId, long amount) {
        validate(requestId, amount);
        WithdrawOrder order = withdrawOrderRepository.findByUserIdAndRequestId(userId, requestId);
        if (order != null) {
            ensureReplayMatches(order, requestId, userId, amount);
            if ("SUCCEEDED".equals(order.getStatus())) {
                return WithdrawOrderResult.from(order);
            }
        }

        accountService.requireUserWalletActive(userId);
        order = order == null ? createOrLoad(requestId, userId, amount) : order;
        ensureReplayMatches(order, requestId, userId, amount);

        if ("REQUESTED".equals(order.getStatus()) || "PROCESSING".equals(order.getStatus())) {
            ledgerService.post(new WalletLedgerCommand(
                    "wallet:test-credit:discard:" + order.getOrderId(),
                    WalletTxnType.TEST_CREDIT_DISCARD,
                    WalletTxnType.TEST_CREDIT_DISCARD.name(),
                    order.getOrderId().toString(),
                    List.of(
                            WalletPosting.debit(accountService.ensureUserWallet(userId), amount),
                            WalletPosting.credit(accountService.ensureSystemAccount(TEST_CREDIT_EXPENSE_ACCOUNT), amount)
                    )
            ));
        }
        if ("REQUESTED".equals(order.getStatus())) {
            withdrawOrderRepository.updateStatus(userId, requestId, "REQUESTED", "PROCESSING");
            order = requireOrder(userId, requestId);
        }
        if ("PROCESSING".equals(order.getStatus())) {
            withdrawOrderRepository.updateStatus(userId, requestId, "PROCESSING", "SUCCEEDED");
        }
        return WithdrawOrderResult.from(requireOrder(userId, requestId));
    }

    private WithdrawOrderResult discardTestCredits(String requestId, UUID userId, long amount) {
        WithdrawOrder existing = withdrawOrderRepository.findByUserIdAndRequestId(userId, requestId);
        if (existing != null) {
            ensureReplayMatches(existing, requestId, userId, amount);
            if ("SUCCEEDED".equals(existing.getStatus())) {
                return WithdrawOrderResult.from(existing);
            }
        }
        if (!testCreditQuotaPort.tryReserveDiscard(
                userId,
                amount,
                testCreditPolicy.properties().getDiscardQuotaPerUser()
        )) {
            throw new BusinessException(WalletErrorCode.TEST_CREDIT_QUOTA_EXCEEDED);
        }
        return discardTestCreditsInternal(requestId, userId, amount);
    }

    private void validate(String requestId, long amount) {
        if (requestId == null || requestId.isBlank()) {
            throw new BusinessException(WalletErrorCode.INVALID_REQUEST, "requestId must not be blank");
        }
        orderDomainService.validatePositiveAmount(amount);
    }

    private WithdrawOrder createOrLoad(String requestId, UUID userId, long amount) {
        WithdrawOrder order = new WithdrawOrder();
        order.setOrderId(idGenerator.next());
        order.setRequestId(requestId);
        order.setUserId(userId);
        order.setAmount(amount);
        order.setStatus("REQUESTED");
        CreationOutcome<WithdrawOrder> outcome = withdrawOrderRepository.create(order);
        if (outcome == null
                || outcome.status() == CreationOutcome.Status.CONFLICT
                || outcome.aggregate() == null) {
            throw new BusinessException(
                    WalletErrorCode.REQUEST_REPLAY_CONFLICT,
                    "withdraw order creation conflict: requestId=" + requestId
            );
        }
        WithdrawOrder persisted = outcome.aggregate();
        ensureReplayMatches(persisted, requestId, userId, amount);
        return persisted;
    }

    private WithdrawOrder requireOrder(UUID userId, String requestId) {
        WithdrawOrder order = withdrawOrderRepository.findByUserIdAndRequestId(userId, requestId);
        if (order == null) {
            throw new BusinessException(WalletErrorCode.INVALID_REQUEST, "withdraw order not found: requestId=" + requestId);
        }
        return order;
    }

    private void ensureReplayMatches(WithdrawOrder order, String requestId, UUID userId, long amount) {
        if (!Objects.equals(requestId, order.getRequestId())
                || !userId.equals(order.getUserId())
                || order.getAmount() != amount) {
            throw new BusinessException(
                    WalletErrorCode.REQUEST_REPLAY_CONFLICT,
                    "requestId replay conflict: requestId=" + order.getRequestId()
            );
        }
    }
}
