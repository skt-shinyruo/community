package com.nowcoder.community.market.service;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.market.dto.VirtualDisputeResponse;
import com.nowcoder.community.market.entity.VirtualDispute;
import com.nowcoder.community.market.entity.VirtualOrder;
import com.nowcoder.community.market.mapper.VirtualDisputeMapper;
import com.nowcoder.community.market.mapper.VirtualOrderMapper;
import com.nowcoder.community.wallet.api.action.WalletMarketActionApi;
import com.nowcoder.community.wallet.api.model.WalletMarketTxnView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Date;
import java.util.List;

import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;
import static com.nowcoder.community.common.exception.CommonErrorCode.NOT_FOUND;

@Service
public class VirtualDisputeService {

    private static final String DISPUTE_STATUS_OPEN = "OPEN";
    private static final String DISPUTE_STATUS_SELLER_ACCEPTED = "SELLER_ACCEPTED";
    private static final String DISPUTE_STATUS_SELLER_REJECTED = "SELLER_REJECTED";
    private static final String DISPUTE_STATUS_ADMIN_RESOLVED = "ADMIN_RESOLVED";
    private static final String ORDER_STATUS_DELIVERED = "DELIVERED";
    private static final String ORDER_STATUS_DISPUTED = "DISPUTED";
    private static final String RESOLUTION_REFUND = "REFUND";
    private static final String RESOLUTION_RELEASE = "RELEASE";

    private final VirtualDisputeMapper virtualDisputeMapper;
    private final VirtualOrderMapper virtualOrderMapper;
    private final WalletMarketActionApi walletMarketActionApi;

    public VirtualDisputeService(VirtualDisputeMapper virtualDisputeMapper,
                                 VirtualOrderMapper virtualOrderMapper,
                                 WalletMarketActionApi walletMarketActionApi) {
        this.virtualDisputeMapper = virtualDisputeMapper;
        this.virtualOrderMapper = virtualOrderMapper;
        this.walletMarketActionApi = walletMarketActionApi;
    }

    @Transactional
    public VirtualDisputeResponse openDispute(long orderId, int buyerUserId, String reason, String buyerNote) {
        validateText(reason, "reason");
        validateText(buyerNote, "buyerNote");
        VirtualOrder order = requireOrderForUpdate(orderId);
        if (order.getBuyerUserId() != buyerUserId) {
            throw new BusinessException(INVALID_ARGUMENT, "buyer does not own order: orderId=" + orderId);
        }
        if (!ORDER_STATUS_DELIVERED.equals(order.getStatus())) {
            throw new BusinessException(INVALID_ARGUMENT, "order is not delivered: orderId=" + orderId);
        }
        boolean activeExists = virtualDisputeMapper.selectByOrderId(orderId).stream().anyMatch(this::isActiveDispute);
        if (activeExists) {
            throw new BusinessException(INVALID_ARGUMENT, "order already has active dispute: orderId=" + orderId);
        }

        VirtualDispute dispute = new VirtualDispute();
        dispute.setOrderId(orderId);
        dispute.setBuyerUserId(order.getBuyerUserId());
        dispute.setSellerUserId(order.getSellerUserId());
        dispute.setStatus(DISPUTE_STATUS_OPEN);
        dispute.setReason(reason.trim());
        dispute.setBuyerNote(buyerNote.trim());
        virtualDisputeMapper.insert(dispute);
        virtualOrderMapper.markDisputed(orderId);
        return VirtualDisputeResponse.from(reloadDispute(dispute.getDisputeId()));
    }

    @Transactional
    public VirtualDisputeResponse sellerAcceptRefund(long disputeId, int sellerUserId, String sellerNote) {
        validateText(sellerNote, "sellerNote");
        VirtualDispute dispute = requireOpenDisputeForSeller(disputeId, sellerUserId);
        VirtualOrder order = requireDisputedOrderForUpdate(dispute.getOrderId());

        WalletMarketTxnView refundTxn = walletMarketActionApi.refundOrder(
                "virtual-order:" + order.getOrderId() + ":refund",
                order.getBuyerUserId(),
                order.getTotalAmount(),
                "virtual-order:" + order.getOrderId()
        );

        dispute.setStatus(DISPUTE_STATUS_SELLER_ACCEPTED);
        dispute.setSellerNote(sellerNote.trim());
        dispute.setResolutionType(RESOLUTION_REFUND);
        dispute.setResolvedAt(new Date());
        virtualDisputeMapper.update(dispute);
        virtualOrderMapper.markRefunded(order.getOrderId(), refundTxn.txnId());
        return VirtualDisputeResponse.from(reloadDispute(disputeId));
    }

    @Transactional
    public VirtualDisputeResponse sellerRejectRefund(long disputeId, int sellerUserId, String sellerNote) {
        validateText(sellerNote, "sellerNote");
        VirtualDispute dispute = requireOpenDisputeForSeller(disputeId, sellerUserId);
        dispute.setStatus(DISPUTE_STATUS_SELLER_REJECTED);
        dispute.setSellerNote(sellerNote.trim());
        virtualDisputeMapper.update(dispute);
        return VirtualDisputeResponse.from(reloadDispute(disputeId));
    }

    @Transactional
    public VirtualDisputeResponse adminResolveRefund(long disputeId, int adminUserId, String note) {
        validateActor(adminUserId);
        validateText(note, "note");
        VirtualDispute dispute = requireAdminResolvableDispute(disputeId);
        VirtualOrder order = requireDisputedOrderForUpdate(dispute.getOrderId());

        WalletMarketTxnView refundTxn = walletMarketActionApi.refundOrder(
                "virtual-order:" + order.getOrderId() + ":refund",
                order.getBuyerUserId(),
                order.getTotalAmount(),
                "virtual-order:" + order.getOrderId()
        );

        dispute.setStatus(DISPUTE_STATUS_ADMIN_RESOLVED);
        dispute.setSellerNote(note.trim());
        dispute.setResolutionType(RESOLUTION_REFUND);
        dispute.setResolvedBy(adminUserId);
        dispute.setResolvedAt(new Date());
        virtualDisputeMapper.update(dispute);
        virtualOrderMapper.markRefunded(order.getOrderId(), refundTxn.txnId());
        return VirtualDisputeResponse.from(reloadDispute(disputeId));
    }

    @Transactional
    public VirtualDisputeResponse adminResolveRelease(long disputeId, int adminUserId, String note) {
        validateActor(adminUserId);
        validateText(note, "note");
        VirtualDispute dispute = requireAdminResolvableDispute(disputeId);
        VirtualOrder order = requireDisputedOrderForUpdate(dispute.getOrderId());

        WalletMarketTxnView releaseTxn = walletMarketActionApi.releaseOrder(
                "virtual-order:" + order.getOrderId() + ":release",
                order.getSellerUserId(),
                order.getTotalAmount(),
                "virtual-order:" + order.getOrderId()
        );

        dispute.setStatus(DISPUTE_STATUS_ADMIN_RESOLVED);
        dispute.setSellerNote(note.trim());
        dispute.setResolutionType(RESOLUTION_RELEASE);
        dispute.setResolvedBy(adminUserId);
        dispute.setResolvedAt(new Date());
        virtualDisputeMapper.update(dispute);
        virtualOrderMapper.markCompleted(order.getOrderId(), releaseTxn.txnId());
        return VirtualDisputeResponse.from(reloadDispute(disputeId));
    }

    public List<VirtualDisputeResponse> listOpenDisputes() {
        return virtualDisputeMapper.selectOpenDisputes().stream()
                .map(VirtualDisputeResponse::from)
                .toList();
    }

    private VirtualDispute requireOpenDisputeForSeller(long disputeId, int sellerUserId) {
        VirtualDispute dispute = reloadDispute(disputeId);
        if (dispute.getSellerUserId() != sellerUserId) {
            throw new BusinessException(INVALID_ARGUMENT, "seller does not own dispute: disputeId=" + disputeId);
        }
        if (!DISPUTE_STATUS_OPEN.equals(dispute.getStatus())) {
            throw new BusinessException(INVALID_ARGUMENT, "dispute is not open: disputeId=" + disputeId);
        }
        return dispute;
    }

    private VirtualDispute requireAdminResolvableDispute(long disputeId) {
        VirtualDispute dispute = reloadDispute(disputeId);
        if (!isActiveDispute(dispute)) {
            throw new BusinessException(INVALID_ARGUMENT, "dispute is not admin-resolvable: disputeId=" + disputeId);
        }
        return dispute;
    }

    private VirtualOrder requireDisputedOrderForUpdate(long orderId) {
        VirtualOrder order = requireOrderForUpdate(orderId);
        if (!ORDER_STATUS_DISPUTED.equals(order.getStatus())) {
            throw new BusinessException(INVALID_ARGUMENT, "order is not disputed: orderId=" + orderId);
        }
        return order;
    }

    private VirtualOrder requireOrderForUpdate(long orderId) {
        VirtualOrder order = virtualOrderMapper.selectByIdForUpdate(orderId);
        if (order == null) {
            throw new BusinessException(NOT_FOUND, "virtual order not found: orderId=" + orderId);
        }
        return order;
    }

    private VirtualDispute reloadDispute(long disputeId) {
        VirtualDispute dispute = virtualDisputeMapper.selectById(disputeId);
        if (dispute == null) {
            throw new BusinessException(NOT_FOUND, "virtual dispute not found: disputeId=" + disputeId);
        }
        return dispute;
    }

    private boolean isActiveDispute(VirtualDispute dispute) {
        return DISPUTE_STATUS_OPEN.equals(dispute.getStatus()) || DISPUTE_STATUS_SELLER_REJECTED.equals(dispute.getStatus());
    }

    private void validateText(String value, String field) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(INVALID_ARGUMENT, field + " must not be blank");
        }
    }

    private void validateActor(int userId) {
        if (userId <= 0) {
            throw new BusinessException(INVALID_ARGUMENT, "actorUserId must be positive");
        }
    }
}
