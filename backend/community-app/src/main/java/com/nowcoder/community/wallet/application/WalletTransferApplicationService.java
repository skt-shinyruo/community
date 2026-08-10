package com.nowcoder.community.wallet.application;

import com.nowcoder.community.common.id.UuidV7Generator;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.idempotency.IdempotencyGuard;
import com.nowcoder.community.common.idempotency.EffectiveIdempotencyKey;
import com.nowcoder.community.common.idempotency.IdempotencyKeyResolver;
import com.nowcoder.community.common.idempotency.RequestFingerprint;
import com.nowcoder.community.wallet.domain.model.TransferOrder;
import com.nowcoder.community.wallet.domain.model.WalletLedgerCommand;
import com.nowcoder.community.wallet.domain.model.WalletPosting;
import com.nowcoder.community.wallet.domain.model.WalletTxnType;
import com.nowcoder.community.wallet.domain.repository.CreationOutcome;
import com.nowcoder.community.wallet.domain.repository.TransferOrderRepository;
import com.nowcoder.community.wallet.domain.service.WalletOrderDomainService;
import com.nowcoder.community.wallet.exception.WalletErrorCode;
import com.nowcoder.community.user.api.query.UserLookupQueryApi;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static com.nowcoder.community.common.exception.CommonErrorCode.NOT_FOUND;

@Service
public class WalletTransferApplicationService {

    public record CreateTransferCommand(UUID fromUserId, UUID toUserId, long amount, String idempotencyKey) {
    }

    public record TransferOrderResult(
            UUID orderId,
            String requestId,
            UUID fromUserId,
            UUID toUserId,
            long amount,
            String status
    ) {

        private static TransferOrderResult from(TransferOrder order) {
            return new TransferOrderResult(
                    order.getOrderId(),
                    order.getRequestId(),
                    order.getFromUserId(),
                    order.getToUserId(),
                    order.getAmount(),
                    order.getStatus()
            );
        }
    }

    private final TransferOrderRepository transferOrderRepository;
    private final WalletAccountApplicationService accountService;
    private final WalletLedgerApplicationService ledgerService;
    private final IdempotencyGuard idempotencyGuard;
    private final WalletOrderDomainService orderDomainService;
    private final UuidV7Generator idGenerator;
    private final UserLookupQueryApi userLookupQueryApi;

    public WalletTransferApplicationService(TransferOrderRepository transferOrderRepository,
                                            WalletAccountApplicationService accountService,
                                            WalletLedgerApplicationService ledgerService,
                                            IdempotencyGuard idempotencyGuard,
                                            UuidV7Generator idGenerator,
                                            UserLookupQueryApi userLookupQueryApi) {
        this.transferOrderRepository = Objects.requireNonNull(transferOrderRepository, "transferOrderRepository must not be null");
        this.accountService = Objects.requireNonNull(accountService, "accountService must not be null");
        this.ledgerService = Objects.requireNonNull(ledgerService, "ledgerService must not be null");
        this.idempotencyGuard = Objects.requireNonNull(idempotencyGuard, "idempotencyGuard must not be null");
        this.orderDomainService = new WalletOrderDomainService();
        this.idGenerator = Objects.requireNonNull(idGenerator, "idGenerator must not be null");
        this.userLookupQueryApi = Objects.requireNonNull(userLookupQueryApi, "userLookupQueryApi must not be null");
    }

    @Transactional
    public TransferOrderResult transfer(CreateTransferCommand command) {
        Objects.requireNonNull(command, "command must not be null");
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
            ensureReplayMatches(existing, requestId, fromUserId, toUserId, amount);
            return TransferOrderResult.from(existing);
        }

        requireRecipientUserExists(toUserId);
        accountService.requireUserWalletActive(fromUserId);

        TransferOrder order = createOrLoad(requestId, fromUserId, toUserId, amount);
        ensureReplayMatches(order, requestId, fromUserId, toUserId, amount);
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
        CreationOutcome<TransferOrder> outcome = transferOrderRepository.create(order);
        if (outcome == null
                || outcome.status() == CreationOutcome.Status.CONFLICT
                || outcome.aggregate() == null) {
            throw new BusinessException(
                    WalletErrorCode.REQUEST_REPLAY_CONFLICT,
                    "transfer order creation conflict: requestId=" + requestId
            );
        }
        TransferOrder persisted = outcome.aggregate();
        ensureReplayMatches(persisted, requestId, fromUserId, toUserId, amount);
        return persisted;
    }

    private TransferOrder requireOrder(UUID fromUserId, String requestId) {
        TransferOrder order = transferOrderRepository.findByFromUserIdAndRequestId(fromUserId, requestId);
        if (order == null) {
            throw new BusinessException(WalletErrorCode.INVALID_REQUEST, "transfer order not found: requestId=" + requestId);
        }
        return order;
    }

    private void ensureReplayMatches(
            TransferOrder order,
            String requestId,
            UUID fromUserId,
            UUID toUserId,
            long amount
    ) {
        if (!Objects.equals(requestId, order.getRequestId())
                || !fromUserId.equals(order.getFromUserId())
                || !toUserId.equals(order.getToUserId())
                || order.getAmount() != amount) {
            throw new BusinessException(
                    WalletErrorCode.REQUEST_REPLAY_CONFLICT,
                    "requestId replay conflict: requestId=" + order.getRequestId()
            );
        }
    }

    private void requireRecipientUserExists(UUID toUserId) {
        if (userLookupQueryApi.getSummaryById(toUserId) == null) {
            throw new BusinessException(NOT_FOUND, "wallet transfer recipient not found: userId=" + toUserId);
        }
    }
}
