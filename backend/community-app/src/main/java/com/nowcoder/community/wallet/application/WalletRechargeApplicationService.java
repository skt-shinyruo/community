package com.nowcoder.community.wallet.application;

import com.nowcoder.community.common.id.UuidV7Generator;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.idempotency.IdempotencyGuard;
import com.nowcoder.community.infra.idempotency.EffectiveIdempotencyKey;
import com.nowcoder.community.infra.idempotency.IdempotencyKeyResolver;
import com.nowcoder.community.infra.idempotency.RequestFingerprint;
import com.nowcoder.community.wallet.application.command.CreateRechargeCommand;
import com.nowcoder.community.wallet.domain.model.RechargeOrder;
import com.nowcoder.community.wallet.application.result.RechargeOrderResult;
import com.nowcoder.community.wallet.domain.model.WalletLedgerCommand;
import com.nowcoder.community.wallet.domain.model.WalletPosting;
import com.nowcoder.community.wallet.domain.model.WalletTxnType;
import com.nowcoder.community.wallet.domain.repository.RechargeOrderRepository;
import com.nowcoder.community.wallet.domain.service.WalletOrderDomainService;
import com.nowcoder.community.wallet.exception.WalletErrorCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class WalletRechargeApplicationService {

    private final RechargeOrderRepository rechargeOrderRepository;
    private final WalletAccountApplicationService accountService;
    private final WalletLedgerApplicationService ledgerService;
    private final IdempotencyGuard idempotencyGuard;
    private final WalletOrderDomainService orderDomainService;
    private final UuidV7Generator idGenerator;

    @Autowired
    public WalletRechargeApplicationService(RechargeOrderRepository rechargeOrderRepository,
                                            WalletAccountApplicationService accountService,
                                            WalletLedgerApplicationService ledgerService,
                                            IdempotencyGuard idempotencyGuard) {
        this(rechargeOrderRepository, accountService, ledgerService, idempotencyGuard, new WalletOrderDomainService(), new UuidV7Generator());
    }

    WalletRechargeApplicationService(RechargeOrderRepository rechargeOrderRepository,
                                     WalletAccountApplicationService accountService,
                                     WalletLedgerApplicationService ledgerService,
                                     IdempotencyGuard idempotencyGuard,
                                     WalletOrderDomainService orderDomainService,
                                     UuidV7Generator idGenerator) {
        this.rechargeOrderRepository = rechargeOrderRepository;
        this.accountService = accountService;
        this.ledgerService = ledgerService;
        this.idempotencyGuard = idempotencyGuard;
        this.orderDomainService = orderDomainService;
        this.idGenerator = idGenerator;
    }

    WalletRechargeApplicationService(RechargeOrderRepository rechargeOrderRepository,
                                     WalletAccountApplicationService accountService,
                                     WalletLedgerApplicationService ledgerService) {
        this(rechargeOrderRepository, accountService, ledgerService, null, new WalletOrderDomainService(), new UuidV7Generator());
    }

    @Transactional
    public RechargeOrderResult recharge(CreateRechargeCommand command) {
        EffectiveIdempotencyKey effective = IdempotencyKeyResolver.resolve(command.idempotencyKey());
        return idempotencyGuard.executeRequired(
                "wallet:recharge",
                command.userId(),
                effective.value(),
                RequestFingerprint.sha256("wallet:recharge|amount=" + command.amount()),
                WalletErrorCode.REQUEST_REPLAY_CONFLICT,
                RechargeOrderResult.class,
                () -> completeInternal(effective.value(), command.userId(), command.amount())
        );
    }

    @Transactional
    public RechargeOrderResult complete(String requestId, UUID userId, long amount) {
        return completeInternal(requestId, userId, amount);
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
                "wallet:recharge:" + order.getOrderId(),
                WalletTxnType.RECHARGE,
                WalletTxnType.RECHARGE.name(),
                order.getOrderId().toString(),
                List.of(
                        WalletPosting.debit(accountService.ensureSystemAccount("PLATFORM_CASH"), amount),
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
        try {
            rechargeOrderRepository.insert(order);
            return order;
        } catch (DataIntegrityViolationException ex) {
            RechargeOrder duplicated = rechargeOrderRepository.findByUserIdAndRequestId(userId, requestId);
            if (duplicated != null) {
                return duplicated;
            }
            throw ex;
        }
    }

    private RechargeOrder requireOrder(UUID userId, String requestId) {
        RechargeOrder order = rechargeOrderRepository.findByUserIdAndRequestId(userId, requestId);
        if (order == null) {
            throw new BusinessException(WalletErrorCode.INVALID_REQUEST, "recharge order not found: requestId=" + requestId);
        }
        return order;
    }
}
