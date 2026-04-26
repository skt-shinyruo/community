package com.nowcoder.community.market.service;

import com.nowcoder.community.common.id.UuidV7Generator;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.market.entity.MarketDispute;
import com.nowcoder.community.market.entity.MarketOrder;
import com.nowcoder.community.market.mapper.MarketDisputeMapper;
import com.nowcoder.community.market.mapper.MarketOrderMapper;
import com.nowcoder.community.market.model.MarketDisputeResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;
import static com.nowcoder.community.common.exception.CommonErrorCode.NOT_FOUND;

@Service
public class MarketDisputeService {

    private static final String DISPUTE_STATUS_OPEN = "OPEN";
    private static final String DISPUTE_STATUS_SELLER_ACCEPTED = "SELLER_ACCEPTED";
    private static final String DISPUTE_STATUS_SELLER_REJECTED = "SELLER_REJECTED";
    private static final String DISPUTE_STATUS_ADMIN_RESOLVED = "ADMIN_RESOLVED";
    private static final String ORDER_STATUS_DELIVERED = "DELIVERED";
    private static final String ORDER_STATUS_SHIPPED = "SHIPPED";
    private static final String ORDER_STATUS_DISPUTED = "DISPUTED";
    private static final String RESOLUTION_REFUND = "REFUND";
    private static final String RESOLUTION_RELEASE = "RELEASE";

    private final MarketDisputeMapper marketDisputeMapper;
    private final MarketOrderMapper marketOrderMapper;
    private final MarketWalletActionService marketWalletActionService;
    private final UuidV7Generator idGenerator;

    @Autowired
    public MarketDisputeService(MarketDisputeMapper marketDisputeMapper,
                                MarketOrderMapper marketOrderMapper,
                                MarketWalletActionService marketWalletActionService) {
        this(marketDisputeMapper, marketOrderMapper, marketWalletActionService, new UuidV7Generator());
    }

    MarketDisputeService(MarketDisputeMapper marketDisputeMapper,
                         MarketOrderMapper marketOrderMapper,
                         MarketWalletActionService marketWalletActionService,
                         UuidV7Generator idGenerator) {
        this.marketDisputeMapper = marketDisputeMapper;
        this.marketOrderMapper = marketOrderMapper;
        this.marketWalletActionService = marketWalletActionService;
        this.idGenerator = idGenerator;
    }

    @Transactional
    public MarketDisputeResult openDispute(UUID orderId, UUID buyerUserId, String reason, String buyerNote) {
        validateText(reason, "reason");
        validateText(buyerNote, "buyerNote");
        MarketOrder order = requireOrderForUpdate(orderId);
        if (!Objects.equals(order.getBuyerUserId(), buyerUserId)) {
            throw new BusinessException(INVALID_ARGUMENT, "buyer does not own order: orderId=" + orderId);
        }
        if (!Set.of(ORDER_STATUS_DELIVERED, ORDER_STATUS_SHIPPED).contains(order.getStatus())) {
            throw new BusinessException(INVALID_ARGUMENT, "order is not disputable: orderId=" + orderId);
        }
        boolean activeExists = marketDisputeMapper.selectByOrderId(orderId).stream().anyMatch(this::isActiveDispute);
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
        marketDisputeMapper.insert(dispute);
        marketOrderMapper.markDisputed(orderId);
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
        marketDisputeMapper.update(dispute);
        marketOrderMapper.markDisputeRefundPending(order.getOrderId());
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
        marketDisputeMapper.update(dispute);
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
        marketDisputeMapper.update(dispute);
        marketOrderMapper.markDisputeRefundPending(order.getOrderId());
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
        marketDisputeMapper.update(dispute);
        marketOrderMapper.markDisputeReleasePending(order.getOrderId());
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
        return marketDisputeMapper.selectOpenDisputes().stream()
                .map(MarketDisputeResult::from)
                .toList();
    }

    private MarketDispute requireOpenDisputeForSeller(UUID disputeId, UUID sellerUserId) {
        MarketDispute dispute = reloadDispute(disputeId);
        if (!Objects.equals(dispute.getSellerUserId(), sellerUserId)) {
            throw new BusinessException(INVALID_ARGUMENT, "seller does not own dispute: disputeId=" + disputeId);
        }
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
        if (!ORDER_STATUS_DISPUTED.equals(order.getStatus())) {
            throw new BusinessException(INVALID_ARGUMENT, "order is not disputed: orderId=" + orderId);
        }
        return order;
    }

    private MarketOrder requireOrderForUpdate(UUID orderId) {
        MarketOrder order = marketOrderMapper.selectByIdForUpdate(orderId);
        if (order == null) {
            throw new BusinessException(NOT_FOUND, "market order not found: orderId=" + orderId);
        }
        return order;
    }

    private MarketDispute reloadDispute(UUID disputeId) {
        MarketDispute dispute = marketDisputeMapper.selectById(disputeId);
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
