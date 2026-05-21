package com.nowcoder.community.wallet.application;

import com.nowcoder.community.common.id.UuidV7Generator;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.idempotency.IdempotencyGuard;
import com.nowcoder.community.infra.idempotency.EffectiveIdempotencyKey;
import com.nowcoder.community.infra.idempotency.IdempotencyKeyResolver;
import com.nowcoder.community.infra.idempotency.RequestFingerprint;
import com.nowcoder.community.wallet.application.command.CreateWithdrawCommand;
import com.nowcoder.community.wallet.application.result.WithdrawOrderResult;
import com.nowcoder.community.wallet.domain.model.WithdrawOrder;
import com.nowcoder.community.wallet.domain.model.WalletLedgerCommand;
import com.nowcoder.community.wallet.domain.model.WalletPosting;
import com.nowcoder.community.wallet.domain.model.WalletTxnType;
import com.nowcoder.community.wallet.domain.repository.WithdrawOrderRepository;
import com.nowcoder.community.wallet.domain.service.WalletOrderDomainService;
import com.nowcoder.community.wallet.exception.WalletErrorCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class WalletWithdrawApplicationService {

    private final WithdrawOrderRepository withdrawOrderRepository;
    private final WalletAccountApplicationService accountService;
    private final WalletLedgerApplicationService ledgerService;
    private final IdempotencyGuard idempotencyGuard;
    private final WalletOrderDomainService orderDomainService;
    private final UuidV7Generator idGenerator;

    @Autowired
    public WalletWithdrawApplicationService(WithdrawOrderRepository withdrawOrderRepository,
                                            WalletAccountApplicationService accountService,
                                            WalletLedgerApplicationService ledgerService,
                                            IdempotencyGuard idempotencyGuard) {
        this(withdrawOrderRepository, accountService, ledgerService, idempotencyGuard, new WalletOrderDomainService(), new UuidV7Generator());
    }

    WalletWithdrawApplicationService(WithdrawOrderRepository withdrawOrderRepository,
                                     WalletAccountApplicationService accountService,
                                     WalletLedgerApplicationService ledgerService,
                                     IdempotencyGuard idempotencyGuard,
                                     WalletOrderDomainService orderDomainService,
                                     UuidV7Generator idGenerator) {
        this.withdrawOrderRepository = withdrawOrderRepository;
        this.accountService = accountService;
        this.ledgerService = ledgerService;
        this.idempotencyGuard = idempotencyGuard;
        this.orderDomainService = orderDomainService;
        this.idGenerator = idGenerator;
    }

    WalletWithdrawApplicationService(WithdrawOrderRepository withdrawOrderRepository,
                                     WalletAccountApplicationService accountService,
                                     WalletLedgerApplicationService ledgerService) {
        this(withdrawOrderRepository, accountService, ledgerService, null, new WalletOrderDomainService(), new UuidV7Generator());
    }

    public WithdrawOrderResult withdraw(CreateWithdrawCommand command) {
        EffectiveIdempotencyKey effective = IdempotencyKeyResolver.resolve(command.idempotencyKey());
        return idempotencyGuard.executeRequired(
                "wallet:withdraw",
                command.userId(),
                effective.value(),
                RequestFingerprint.sha256("wallet:withdraw|amount=" + command.amount()),
                WalletErrorCode.REQUEST_REPLAY_CONFLICT,
                WithdrawOrderResult.class,
                () -> request(effective.value(), command.userId(), command.amount())
        );
    }

    @Transactional
    public WithdrawOrderResult request(String requestId, UUID userId, long amount) {
        validate(requestId, amount);
        WithdrawOrder order = withdrawOrderRepository.findByUserIdAndRequestId(userId, requestId);
        if (order != null) {
            ensureReplayMatches(order, userId, amount);
            if ("SUCCEEDED".equals(order.getStatus())) {
                return WithdrawOrderResult.from(order);
            }
        }

        accountService.requireUserWalletActive(userId);

        if (order == null && accountService.balanceOfSystem("PLATFORM_CASH") < amount) {
            order = withdrawOrderRepository.findByUserIdAndRequestId(userId, requestId);
            if (order == null) {
                throw new BusinessException(WalletErrorCode.PLATFORM_CASH_INSUFFICIENT, "platform cash insufficient");
            }
        }

        order = order == null ? createOrLoad(requestId, userId, amount) : order;
        ensureReplayMatches(order, userId, amount);
        if ("SUCCEEDED".equals(order.getStatus())) {
            return WithdrawOrderResult.from(order);
        }

        if ("REQUESTED".equals(order.getStatus())) {
            ledgerService.post(new WalletLedgerCommand(
                    "wallet:withdraw:" + order.getOrderId() + ":request",
                    WalletTxnType.WITHDRAW,
                    WalletTxnType.WITHDRAW.name(),
                    order.getOrderId().toString(),
                    List.of(
                            WalletPosting.debit(accountService.ensureUserWallet(userId), amount),
                            WalletPosting.credit(accountService.ensureSystemAccount("WITHDRAW_PENDING"), amount)
                    )
            ));
            withdrawOrderRepository.updateStatus(userId, requestId, "REQUESTED", "PROCESSING");
            order = requireOrder(userId, requestId);
        }

        if ("PROCESSING".equals(order.getStatus())) {
            ledgerService.post(new WalletLedgerCommand(
                    "wallet:withdraw:" + order.getOrderId() + ":settle",
                    WalletTxnType.WITHDRAW,
                    WalletTxnType.WITHDRAW.name(),
                    order.getOrderId().toString(),
                    List.of(
                            WalletPosting.debit(accountService.ensureSystemAccount("WITHDRAW_PENDING"), amount),
                            WalletPosting.credit(accountService.ensureSystemAccount("PLATFORM_CASH"), amount)
                    )
            ));
            withdrawOrderRepository.updateStatus(userId, requestId, "PROCESSING", "SUCCEEDED");
        }
        return WithdrawOrderResult.from(requireOrder(userId, requestId));
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
        try {
            withdrawOrderRepository.insert(order);
            return order;
        } catch (DataIntegrityViolationException ex) {
            WithdrawOrder duplicated = withdrawOrderRepository.findByUserIdAndRequestId(userId, requestId);
            if (duplicated != null) {
                return duplicated;
            }
            throw ex;
        }
    }

    private WithdrawOrder requireOrder(UUID userId, String requestId) {
        WithdrawOrder order = withdrawOrderRepository.findByUserIdAndRequestId(userId, requestId);
        if (order == null) {
            throw new BusinessException(WalletErrorCode.INVALID_REQUEST, "withdraw order not found: requestId=" + requestId);
        }
        return order;
    }

    private void ensureReplayMatches(WithdrawOrder order, UUID userId, long amount) {
        if (!userId.equals(order.getUserId()) || order.getAmount() != amount) {
            throw new BusinessException(
                    WalletErrorCode.REQUEST_REPLAY_CONFLICT,
                    "requestId replay conflict: requestId=" + order.getRequestId()
            );
        }
    }
}
