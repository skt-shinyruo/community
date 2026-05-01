package com.nowcoder.community.market.application;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.id.UuidV7Generator;
import com.nowcoder.community.market.domain.model.MarketWalletAction;
import com.nowcoder.community.market.exception.MarketErrorCode;
import com.nowcoder.community.market.domain.repository.MarketWalletActionRepository;
import com.nowcoder.community.market.domain.model.MarketWalletActionResultType;
import com.nowcoder.community.market.domain.model.MarketWalletActionStatus;
import com.nowcoder.community.market.domain.model.MarketWalletActionType;
import com.nowcoder.community.market.domain.service.MarketWalletActionDomainService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.UUID;

@Service
public class MarketWalletActionApplicationService {

    private final MarketWalletActionRepository walletActionRepository;
    private final UuidV7Generator idGenerator;
    private final MarketWalletActionDomainService walletActionDomainService = new MarketWalletActionDomainService();

    @Autowired
    public MarketWalletActionApplicationService(MarketWalletActionRepository walletActionRepository) {
        this(walletActionRepository, new UuidV7Generator());
    }

    MarketWalletActionApplicationService(MarketWalletActionRepository walletActionRepository, UuidV7Generator idGenerator) {
        this.walletActionRepository = walletActionRepository;
        this.idGenerator = idGenerator;
    }

    @Transactional
    public MarketWalletAction enqueueEscrow(UUID orderId, UUID buyerUserId, UUID sellerUserId, long amount) {
        return enqueue(orderId, null, MarketWalletActionType.ESCROW, buyerUserId, sellerUserId, amount);
    }

    @Transactional
    public MarketWalletAction enqueueRelease(UUID orderId, UUID sellerUserId, UUID buyerUserId, long amount) {
        return enqueue(orderId, null, MarketWalletActionType.RELEASE, sellerUserId, buyerUserId, amount);
    }

    @Transactional
    public MarketWalletAction enqueueRefund(UUID orderId, UUID buyerUserId, UUID sellerUserId, long amount) {
        return enqueue(orderId, null, MarketWalletActionType.REFUND, buyerUserId, sellerUserId, amount);
    }

    @Transactional
    public MarketWalletAction enqueueDisputeRefund(UUID orderId,
                                                   UUID disputeId,
                                                   UUID buyerUserId,
                                                   UUID sellerUserId,
                                                   long amount) {
        return enqueue(orderId, disputeId, MarketWalletActionType.REFUND, buyerUserId, sellerUserId, amount);
    }

    @Transactional
    public MarketWalletAction enqueueDisputeRelease(UUID orderId,
                                                    UUID disputeId,
                                                    UUID sellerUserId,
                                                    UUID buyerUserId,
                                                    long amount) {
        return enqueue(orderId, disputeId, MarketWalletActionType.RELEASE, sellerUserId, buyerUserId, amount);
    }

    @Transactional
    public boolean cancelPendingEscrowIfPossible(UUID orderId) {
        return walletActionRepository.cancelPendingEscrow(
                requestId(orderId, MarketWalletActionType.ESCROW),
                MarketWalletActionResultType.NOOP
        ) == 1;
    }

    private MarketWalletAction enqueue(UUID orderId,
                                       UUID disputeId,
                                       String actionType,
                                       UUID actorUserId,
                                       UUID counterpartyUserId,
                                       long amount) {
        String requestId = requestId(orderId, actionType);
        MarketWalletAction existing = walletActionRepository.findByRequestId(requestId);
        if (existing != null) {
            ensureReplayMatches(existing, orderId, disputeId, actionType, actorUserId, counterpartyUserId, amount);
            return existing;
        }

        MarketWalletAction action = new MarketWalletAction();
        action.setActionId(idGenerator.next());
        action.setOrderId(orderId);
        action.setDisputeId(disputeId);
        action.setActionType(actionType);
        action.setRequestId(requestId);
        action.setWalletBizId(walletBizId(orderId));
        action.setActorUserId(actorUserId);
        action.setCounterpartyUserId(counterpartyUserId);
        action.setAmount(amount);
        action.setStatus(MarketWalletActionStatus.PENDING);
        try {
            walletActionRepository.save(action);
            return action;
        } catch (DataIntegrityViolationException ex) {
            MarketWalletAction duplicated = walletActionRepository.findByRequestId(requestId);
            if (duplicated != null) {
                ensureReplayMatches(duplicated, orderId, disputeId, actionType, actorUserId, counterpartyUserId, amount);
                return duplicated;
            }
            throw ex;
        }
    }

    private String requestId(UUID orderId, String actionType) {
        return walletActionDomainService.requestId(actionType, orderId);
    }

    private String walletBizId(UUID orderId) {
        return "market-order:" + orderId;
    }

    private void ensureReplayMatches(MarketWalletAction existing,
                                     UUID orderId,
                                     UUID disputeId,
                                     String actionType,
                                     UUID actorUserId,
                                     UUID counterpartyUserId,
                                     long amount) {
        boolean matches = Objects.equals(existing.getOrderId(), orderId)
                && Objects.equals(existing.getDisputeId(), disputeId)
                && Objects.equals(existing.getActionType(), actionType)
                && Objects.equals(existing.getActorUserId(), actorUserId)
                && Objects.equals(existing.getCounterpartyUserId(), counterpartyUserId)
                && existing.getAmount() == amount;
        if (!matches) {
            throw new BusinessException(
                    MarketErrorCode.REQUEST_REPLAY_CONFLICT,
                    "market wallet action request replay conflict: requestId=" + existing.getRequestId()
            );
        }
    }
}
