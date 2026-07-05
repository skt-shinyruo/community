package com.nowcoder.community.wallet.application;

import com.nowcoder.community.common.id.UuidV7Generator;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.idempotency.IdempotencyGuard;
import com.nowcoder.community.infra.idempotency.EffectiveIdempotencyKey;
import com.nowcoder.community.infra.idempotency.IdempotencyKeyResolver;
import com.nowcoder.community.infra.idempotency.RequestFingerprint;
import com.nowcoder.community.wallet.application.command.CreateTransferCommand;
import com.nowcoder.community.wallet.application.result.TransferOrderResult;
import com.nowcoder.community.wallet.domain.model.TransferOrder;
import com.nowcoder.community.wallet.domain.model.WalletLedgerCommand;
import com.nowcoder.community.wallet.domain.model.WalletPosting;
import com.nowcoder.community.wallet.domain.model.WalletTxnType;
import com.nowcoder.community.wallet.domain.repository.TransferOrderRepository;
import com.nowcoder.community.wallet.domain.service.WalletOrderDomainService;
import com.nowcoder.community.wallet.exception.WalletErrorCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class WalletTransferApplicationService {

    private final TransferOrderRepository transferOrderRepository;
    private final WalletAccountApplicationService accountService;
    private final WalletLedgerApplicationService ledgerService;
    private final IdempotencyGuard idempotencyGuard;
    private final WalletOrderDomainService orderDomainService;
    private final UuidV7Generator idGenerator;

    @Autowired
    public WalletTransferApplicationService(TransferOrderRepository transferOrderRepository,
                                            WalletAccountApplicationService accountService,
                                            WalletLedgerApplicationService ledgerService,
                                            IdempotencyGuard idempotencyGuard) {
        this(transferOrderRepository, accountService, ledgerService, idempotencyGuard, new WalletOrderDomainService(), new UuidV7Generator());
    }

    WalletTransferApplicationService(TransferOrderRepository transferOrderRepository,
                                     WalletAccountApplicationService accountService,
                                     WalletLedgerApplicationService ledgerService,
                                     IdempotencyGuard idempotencyGuard,
                                     WalletOrderDomainService orderDomainService,
                                     UuidV7Generator idGenerator) {
        this.transferOrderRepository = transferOrderRepository;
        this.accountService = accountService;
        this.ledgerService = ledgerService;
        this.idempotencyGuard = idempotencyGuard;
        this.orderDomainService = orderDomainService;
        this.idGenerator = idGenerator;
    }

    WalletTransferApplicationService(TransferOrderRepository transferOrderRepository,
                                     WalletAccountApplicationService accountService,
                                     WalletLedgerApplicationService ledgerService) {
        this(transferOrderRepository, accountService, ledgerService, null, new WalletOrderDomainService(), new UuidV7Generator());
    }

    @Transactional
    public TransferOrderResult transfer(CreateTransferCommand command) {
        EffectiveIdempotencyKey effective = IdempotencyKeyResolver.resolve(command.idempotencyKey());
        String requestHash = RequestFingerprint.sha256(
                "wallet:transfer|toUserId=" + command.toUserId() + "|amount=" + command.amount()
        );
        return idempotencyGuard.executeRequired(
                "wallet:transfer",
                command.fromUserId(),
                effective.value(),
                requestHash,
                WalletErrorCode.REQUEST_REPLAY_CONFLICT,
                TransferOrderResult.class,
                () -> createInternal(
                        effective.value(),
                        command.fromUserId(),
                        command.toUserId(),
                        command.amount()
                )
        );
    }

    @Transactional
    public TransferOrderResult create(String requestId, UUID fromUserId, UUID toUserId, long amount) {
        return createInternal(requestId, fromUserId, toUserId, amount);
    }

    private TransferOrderResult createInternal(String requestId, UUID fromUserId, UUID toUserId, long amount) {
        validate(requestId, amount);
        orderDomainService.validateTransfer(fromUserId, toUserId, amount);

        TransferOrder existing = transferOrderRepository.findByFromUserIdAndRequestId(fromUserId, requestId);
        if (existing != null) {
            ensureReplayMatches(existing, fromUserId, toUserId, amount);
            return TransferOrderResult.from(existing);
        }

        accountService.requireUserWalletActive(fromUserId);

        TransferOrder order = createOrLoad(requestId, fromUserId, toUserId, amount);
        ensureReplayMatches(order, fromUserId, toUserId, amount);
        if (!"SUCCEEDED".equals(order.getStatus())) {
            ledgerService.post(new WalletLedgerCommand(
                    "wallet:transfer:" + order.getOrderId(),
                    WalletTxnType.TRANSFER,
                    WalletTxnType.TRANSFER.name(),
                    order.getOrderId().toString(),
                    List.of(
                            WalletPosting.debit(accountService.ensureUserWallet(fromUserId), amount),
                            WalletPosting.credit(accountService.ensureUserWallet(toUserId), amount)
                    )
            ));
            transferOrderRepository.updateStatus(fromUserId, requestId, "CREATED", "SUCCEEDED");
        }
        return TransferOrderResult.from(requireOrder(fromUserId, requestId));
    }

    private void validate(String requestId, long amount) {
        if (requestId == null || requestId.isBlank()) {
            throw new BusinessException(WalletErrorCode.INVALID_REQUEST, "requestId must not be blank");
        }
        orderDomainService.validatePositiveAmount(amount);
    }

    private TransferOrder createOrLoad(String requestId, UUID fromUserId, UUID toUserId, long amount) {
        TransferOrder order = new TransferOrder();
        order.setOrderId(idGenerator.next());
        order.setRequestId(requestId);
        order.setFromUserId(fromUserId);
        order.setToUserId(toUserId);
        order.setAmount(amount);
        order.setStatus("CREATED");
        try {
            transferOrderRepository.insert(order);
            return order;
        } catch (DataIntegrityViolationException ex) {
            TransferOrder duplicated = transferOrderRepository.findByFromUserIdAndRequestId(fromUserId, requestId);
            if (duplicated != null) {
                return duplicated;
            }
            throw ex;
        }
    }

    private TransferOrder requireOrder(UUID fromUserId, String requestId) {
        TransferOrder order = transferOrderRepository.findByFromUserIdAndRequestId(fromUserId, requestId);
        if (order == null) {
            throw new BusinessException(WalletErrorCode.INVALID_REQUEST, "transfer order not found: requestId=" + requestId);
        }
        return order;
    }

    private void ensureReplayMatches(TransferOrder order, UUID fromUserId, UUID toUserId, long amount) {
        if (!fromUserId.equals(order.getFromUserId()) || !toUserId.equals(order.getToUserId()) || order.getAmount() != amount) {
            throw new BusinessException(
                    WalletErrorCode.REQUEST_REPLAY_CONFLICT,
                    "requestId replay conflict: requestId=" + order.getRequestId()
            );
        }
    }
}
