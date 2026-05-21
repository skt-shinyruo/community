package com.nowcoder.community.market.application;

import com.nowcoder.community.common.id.UuidV7Generator;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.market.domain.model.MarketDispute;
import com.nowcoder.community.market.domain.model.MarketOrder;
import com.nowcoder.community.market.domain.model.MarketOrderTransition;
import com.nowcoder.community.market.domain.repository.MarketDisputeRepository;
import com.nowcoder.community.market.domain.repository.MarketOrderRepository;
import com.nowcoder.community.market.domain.service.MarketDisputeDomainService;
import com.nowcoder.community.market.application.result.MarketDisputeResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;
import static com.nowcoder.community.common.exception.CommonErrorCode.NOT_FOUND;

@Service
public class MarketDisputeApplicationService {

    private static final String DISPUTE_STATUS_OPEN = "OPEN";
    private static final String DISPUTE_STATUS_SELLER_ACCEPTED = "SELLER_ACCEPTED";
    private static final String DISPUTE_STATUS_SELLER_REJECTED = "SELLER_REJECTED";
    private static final String DISPUTE_STATUS_ADMIN_RESOLVED = "ADMIN_RESOLVED";
    private static final String RESOLUTION_REFUND = "REFUND";
    private static final String RESOLUTION_RELEASE = "RELEASE";

    private final MarketDisputeRepository marketDisputeRepository;
    private final MarketOrderRepository marketOrderRepository;
    private final MarketWalletActionApplicationService marketWalletActionService;
    private final UuidV7Generator idGenerator;
    private final MarketDisputeDomainService disputeDomainService = new MarketDisputeDomainService();

    @Autowired
    public MarketDisputeApplicationService(MarketDisputeRepository marketDisputeRepository,
                                MarketOrderRepository marketOrderRepository,
                                MarketWalletActionApplicationService marketWalletActionService) {
        this(marketDisputeRepository, marketOrderRepository, marketWalletActionService, new UuidV7Generator());
    }

    MarketDisputeApplicationService(MarketDisputeRepository marketDisputeRepository,
                         MarketOrderRepository marketOrderRepository,
                         MarketWalletActionApplicationService marketWalletActionService,
                         UuidV7Generator idGenerator) {
        this.marketDisputeRepository = marketDisputeRepository;
        this.marketOrderRepository = marketOrderRepository;
        this.marketWalletActionService = marketWalletActionService;
        this.idGenerator = idGenerator;
    }

    @Transactional
    public MarketDisputeResult openDispute(UUID orderId, UUID buyerUserId, String reason, String buyerNote) {
        validateText(reason, "reason");
        validateText(buyerNote, "buyerNote");
        MarketOrder order = requireOrderForUpdate(orderId);
        disputeDomainService.validateBuyerCanOpen(buyerUserId, order.getBuyerUserId());
        MarketOrderTransition transition = order.openDispute();
        boolean activeExists = marketDisputeRepository.findByOrderId(orderId).stream().anyMatch(this::isActiveDispute);
        if (activeExists) {
            throw new BusinessException(INVALID_ARGUMENT, "order already has active dispute: orderId=" + orderId);
        }

        MarketDispute dispute = new MarketDispute();
        dispute.setDisputeId(idGenerator.next());
        dispute.setOrderId(orderId);
        dispute.setGoodsType(order.getGoodsType());
        dispute.setBuyerUserId(order.getBuyerUserId());
        dispute.setSellerUserId(order.getSellerUserId());
        dispute.setStatus(DISPUTE_STATUS_OPEN);
        dispute.setReason(reason.trim());
        dispute.setBuyerNote(buyerNote.trim());
        marketDisputeRepository.save(dispute);
        marketOrderRepository.markDisputed(transition.orderId());
        return MarketDisputeResult.from(reloadDispute(dispute.getDisputeId()));
    }

    @Transactional
    public MarketDisputeResult sellerAcceptRefund(UUID disputeId, UUID sellerUserId, String sellerNote) {
        validateText(sellerNote, "sellerNote");
        MarketDispute dispute = requireOpenDisputeForSeller(disputeId, sellerUserId);
        MarketOrder order = requireDisputedOrderForUpdate(dispute.getOrderId());

        dispute.setStatus(DISPUTE_STATUS_SELLER_ACCEPTED);
        dispute.setSellerNote(sellerNote.trim());
        dispute.setResolutionType(RESOLUTION_REFUND);
        dispute.setResolvedAt(new Date());
        marketDisputeRepository.saveChanges(dispute);
        marketOrderRepository.markDisputeRefundPending(order.requestDisputeRefund().orderId());
        marketWalletActionService.enqueueDisputeRefund(
                order.getOrderId(),
                disputeId,
                order.getBuyerUserId(),
                order.getSellerUserId(),
                order.getTotalAmount()
        );
        return MarketDisputeResult.from(reloadDispute(disputeId));
    }

    @Transactional
    public MarketDisputeResult sellerRejectRefund(UUID disputeId, UUID sellerUserId, String sellerNote) {
        validateText(sellerNote, "sellerNote");
        MarketDispute dispute = requireOpenDisputeForSeller(disputeId, sellerUserId);
        dispute.setStatus(DISPUTE_STATUS_SELLER_REJECTED);
        dispute.setSellerNote(sellerNote.trim());
        marketDisputeRepository.saveChanges(dispute);
        return MarketDisputeResult.from(reloadDispute(disputeId));
    }

    @Transactional
    public MarketDisputeResult adminResolveRefund(UUID disputeId, UUID adminUserId, String note) {
        validateActor(adminUserId);
        validateText(note, "note");
        MarketDispute dispute = requireAdminResolvableDispute(disputeId);
        MarketOrder order = requireDisputedOrderForUpdate(dispute.getOrderId());

        dispute.setStatus(DISPUTE_STATUS_ADMIN_RESOLVED);
        dispute.setSellerNote(note.trim());
        dispute.setResolutionType(RESOLUTION_REFUND);
        dispute.setResolvedBy(adminUserId);
        dispute.setResolvedAt(new Date());
        marketDisputeRepository.saveChanges(dispute);
        marketOrderRepository.markDisputeRefundPending(order.requestDisputeRefund().orderId());
        marketWalletActionService.enqueueDisputeRefund(
                order.getOrderId(),
                disputeId,
                order.getBuyerUserId(),
                order.getSellerUserId(),
                order.getTotalAmount()
        );
        return MarketDisputeResult.from(reloadDispute(disputeId));
    }

    @Transactional
    public MarketDisputeResult adminResolveRelease(UUID disputeId, UUID adminUserId, String note) {
        validateActor(adminUserId);
        validateText(note, "note");
        MarketDispute dispute = requireAdminResolvableDispute(disputeId);
        MarketOrder order = requireDisputedOrderForUpdate(dispute.getOrderId());

        dispute.setStatus(DISPUTE_STATUS_ADMIN_RESOLVED);
        dispute.setSellerNote(note.trim());
        dispute.setResolutionType(RESOLUTION_RELEASE);
        dispute.setResolvedBy(adminUserId);
        dispute.setResolvedAt(new Date());
        marketDisputeRepository.saveChanges(dispute);
        marketOrderRepository.markDisputeReleasePending(order.requestDisputeRelease().orderId());
        marketWalletActionService.enqueueDisputeRelease(
                order.getOrderId(),
                disputeId,
                order.getSellerUserId(),
                order.getBuyerUserId(),
                order.getTotalAmount()
        );
        return MarketDisputeResult.from(reloadDispute(disputeId));
    }

    public List<MarketDisputeResult> listOpenDisputes() {
        return marketDisputeRepository.findOpenDisputes().stream()
                .map(MarketDisputeResult::from)
                .toList();
    }

    private MarketDispute requireOpenDisputeForSeller(UUID disputeId, UUID sellerUserId) {
        MarketDispute dispute = reloadDispute(disputeId);
        disputeDomainService.validateSellerCanResolve(sellerUserId, dispute.getSellerUserId());
        if (!DISPUTE_STATUS_OPEN.equals(dispute.getStatus())) {
            throw new BusinessException(INVALID_ARGUMENT, "dispute is not open: disputeId=" + disputeId);
        }
        return dispute;
    }

    private MarketDispute requireAdminResolvableDispute(UUID disputeId) {
        MarketDispute dispute = reloadDispute(disputeId);
        if (!isActiveDispute(dispute)) {
            throw new BusinessException(INVALID_ARGUMENT, "dispute is not admin-resolvable: disputeId=" + disputeId);
        }
        return dispute;
    }

    private MarketOrder requireDisputedOrderForUpdate(UUID orderId) {
        MarketOrder order = requireOrderForUpdate(orderId);
        order.assertDisputed();
        return order;
    }

    private MarketOrder requireOrderForUpdate(UUID orderId) {
        MarketOrder order = marketOrderRepository.lockById(orderId);
        if (order == null) {
            throw new BusinessException(NOT_FOUND, "market order not found: orderId=" + orderId);
        }
        return order;
    }

    private MarketDispute reloadDispute(UUID disputeId) {
        MarketDispute dispute = marketDisputeRepository.findById(disputeId);
        if (dispute == null) {
            throw new BusinessException(NOT_FOUND, "market dispute not found: disputeId=" + disputeId);
        }
        return dispute;
    }

    private boolean isActiveDispute(MarketDispute dispute) {
        return DISPUTE_STATUS_OPEN.equals(dispute.getStatus()) || DISPUTE_STATUS_SELLER_REJECTED.equals(dispute.getStatus());
    }

    private void validateText(String value, String field) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(INVALID_ARGUMENT, field + " must not be blank");
        }
    }

    private void validateActor(UUID userId) {
        if (userId == null) {
            throw new BusinessException(INVALID_ARGUMENT, "actorUserId must not be null");
        }
    }
}
