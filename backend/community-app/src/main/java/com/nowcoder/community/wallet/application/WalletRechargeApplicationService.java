package com.nowcoder.community.wallet.application;

import com.nowcoder.community.common.id.UuidV7Generator;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.idempotency.IdempotencyGuard;
import com.nowcoder.community.common.idempotency.EffectiveIdempotencyKey;
import com.nowcoder.community.common.idempotency.IdempotencyKeyResolver;
import com.nowcoder.community.common.idempotency.RequestFingerprint;
import com.nowcoder.community.wallet.application.command.CreateRechargeCommand;
import com.nowcoder.community.wallet.domain.model.RechargeOrder;
import com.nowcoder.community.wallet.application.result.RechargeOrderResult;
import com.nowcoder.community.wallet.domain.model.WalletLedgerCommand;
import com.nowcoder.community.wallet.domain.model.WalletPosting;
import com.nowcoder.community.wallet.domain.model.WalletTxnType;
import com.nowcoder.community.wallet.domain.repository.CreationOutcome;
import com.nowcoder.community.wallet.domain.repository.RechargeOrderRepository;
import com.nowcoder.community.wallet.domain.service.WalletOrderDomainService;
import com.nowcoder.community.wallet.exception.WalletErrorCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class WalletRechargeApplicationService {

    private static final String TEST_CREDIT_EXPENSE_ACCOUNT = "PLATFORM_TEST_CREDIT_EXPENSE";

    private final RechargeOrderRepository rechargeOrderRepository;
    private final WalletAccountApplicationService accountService;
    private final WalletLedgerApplicationService ledgerService;
    private final IdempotencyGuard idempotencyGuard;
    private final WalletOrderDomainService orderDomainService;
    private final UuidV7Generator idGenerator;
    private final WalletTestCreditPolicy testCreditPolicy;
    private final WalletTestCreditQuotaPort testCreditQuotaPort;

    @Autowired
    public WalletRechargeApplicationService(RechargeOrderRepository rechargeOrderRepository,
                                            WalletAccountApplicationService accountService,
                                            WalletLedgerApplicationService ledgerService,
                                            IdempotencyGuard idempotencyGuard,
                                            WalletTestCreditPolicy testCreditPolicy,
                                            WalletTestCreditQuotaPort testCreditQuotaPort) {
        this(
                rechargeOrderRepository,
                accountService,
                ledgerService,
                idempotencyGuard,
                new WalletOrderDomainService(),
                new UuidV7Generator(),
                testCreditPolicy,
                testCreditQuotaPort
        );
    }

    WalletRechargeApplicationService(RechargeOrderRepository rechargeOrderRepository,
                                     WalletAccountApplicationService accountService,
                                     WalletLedgerApplicationService ledgerService,
                                     IdempotencyGuard idempotencyGuard,
                                     WalletOrderDomainService orderDomainService,
                                     UuidV7Generator idGenerator,
                                     WalletTestCreditPolicy testCreditPolicy,
                                     WalletTestCreditQuotaPort testCreditQuotaPort) {
        this.rechargeOrderRepository = rechargeOrderRepository;
        this.accountService = accountService;
        this.ledgerService = ledgerService;
        this.idempotencyGuard = idempotencyGuard;
        this.orderDomainService = orderDomainService;
        this.idGenerator = idGenerator;
        this.testCreditPolicy = testCreditPolicy;
        this.testCreditQuotaPort = testCreditQuotaPort;
    }

    @Transactional
    public RechargeOrderResult recharge(CreateRechargeCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        orderDomainService.validatePositiveAmount(command.amount());
        testCreditPolicy.assertGrantAllowed(command.amount());
        EffectiveIdempotencyKey effective = IdempotencyKeyResolver.resolve(command.idempotencyKey());
        return idempotencyGuard.executeRequired(
                "wallet:recharge",
                command.userId(),
                effective.value(),
                RequestFingerprint.sha256("wallet:recharge|amount=" + command.amount()),
                WalletErrorCode.REQUEST_REPLAY_CONFLICT,
                RechargeOrderResult.class,
                () -> grantTestCredits(effective.value(), command.userId(), command.amount())
        );
    }

    private RechargeOrderResult grantTestCredits(String requestId, UUID userId, long amount) {
        RechargeOrder existing = rechargeOrderRepository.findByUserIdAndRequestId(userId, requestId);
        if (existing != null) {
            existing.assertReplayMatches(userId, amount);
            if (existing.isPaid()) {
                return RechargeOrderResult.from(existing);
            }
        }
        if (!testCreditQuotaPort.tryReserveGrant(
                userId,
                amount,
                testCreditPolicy.properties().getGrantQuotaPerUser()
        )) {
            throw new BusinessException(WalletErrorCode.TEST_CREDIT_QUOTA_EXCEEDED);
        }
        return completeInternal(
                requestId,
                userId,
                amount
        );
    }

    private RechargeOrderResult completeInternal(String requestId, UUID userId, long amount) {
        validate(requestId, amount);

        RechargeOrder existing = rechargeOrderRepository.findByUserIdAndRequestId(userId, requestId);
        if (existing != null) {
            existing.assertReplayMatches(userId, amount);
            if (existing.isPaid()) {
                return RechargeOrderResult.from(existing);
            }
        }

        RechargeOrder order = existing == null ? createOrLoad(requestId, userId, amount) : existing;
        order.assertReplayMatches(userId, amount);
        if (order.isPaid()) {
            return RechargeOrderResult.from(order);
        }

        ledgerService.post(new WalletLedgerCommand(
                "wallet:test-credit:grant:" + order.getOrderId(),
                WalletTxnType.TEST_CREDIT_GRANT,
                WalletTxnType.TEST_CREDIT_GRANT.name(),
                order.getOrderId().toString(),
                List.of(
                        WalletPosting.debit(accountService.ensureSystemAccount(TEST_CREDIT_EXPENSE_ACCOUNT), amount),
                        WalletPosting.credit(accountService.ensureUserWallet(userId), amount)
                )
        ));
        rechargeOrderRepository.applyTransition(order.pay());
        return RechargeOrderResult.from(requireOrder(userId, requestId));
    }

    private void validate(String requestId, long amount) {
        if (requestId == null || requestId.isBlank()) {
            throw new BusinessException(WalletErrorCode.INVALID_REQUEST, "requestId must not be blank");
        }
        orderDomainService.validatePositiveAmount(amount);
    }

    private RechargeOrder createOrLoad(String requestId, UUID userId, long amount) {
        RechargeOrder order = RechargeOrder.create(idGenerator.next(), requestId, userId, amount);
        CreationOutcome<RechargeOrder> outcome = rechargeOrderRepository.create(order);
        if (outcome == null
                || outcome.status() == CreationOutcome.Status.CONFLICT
                || outcome.aggregate() == null) {
            throw new BusinessException(
                    WalletErrorCode.REQUEST_REPLAY_CONFLICT,
                    "recharge order creation conflict: requestId=" + requestId
            );
        }
        RechargeOrder persisted = outcome.aggregate();
        if (!Objects.equals(requestId, persisted.getRequestId())) {
            throw new BusinessException(
                    WalletErrorCode.REQUEST_REPLAY_CONFLICT,
                    "requestId replay conflict: requestId=" + requestId
            );
        }
        persisted.assertReplayMatches(userId, amount);
        return persisted;
    }

    private RechargeOrder requireOrder(UUID userId, String requestId) {
        RechargeOrder order = rechargeOrderRepository.findByUserIdAndRequestId(userId, requestId);
        if (order == null) {
            throw new BusinessException(WalletErrorCode.INVALID_REQUEST, "recharge order not found: requestId=" + requestId);
        }
        return order;
    }
}
