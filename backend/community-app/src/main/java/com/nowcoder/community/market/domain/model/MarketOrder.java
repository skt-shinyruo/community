package com.nowcoder.community.market.domain.model;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.market.exception.MarketErrorCode;

import java.util.Date;
import java.util.Objects;
import java.util.UUID;

import static com.nowcoder.community.common.exception.CommonErrorCode.FORBIDDEN;
import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;

public class MarketOrder {

    private UUID orderId;
    private String requestId;
    private UUID listingId;
    private String goodsType;
    private UUID sellerUserId;
    private UUID buyerUserId;
    private int quantity;
    private long unitPriceSnapshot;
    private long totalAmount;
    private String deliveryModeSnapshot;
    private String listingTitleSnapshot;
    private String status;
    private UUID escrowTxnId;
    private UUID releaseTxnId;
    private UUID refundTxnId;
    private Date autoConfirmAt;
    private UUID addressIdSnapshot;
    private String receiverNameSnapshot;
    private String receiverPhoneSnapshot;
    private String provinceSnapshot;
    private String citySnapshot;
    private String districtSnapshot;
    private String detailAddressSnapshot;
    private String postalCodeSnapshot;
    private Date createTime;
    private Date updateTime;

    private MarketOrder() {
    }

    public static MarketOrder place(MarketOrderPlacement placement) {
        Objects.requireNonNull(placement, "placement must not be null");
        MarketOrder order = new MarketOrder();
        order.orderId = placement.orderId();
        order.requestId = placement.requestId();
        order.listingId = placement.listingId();
        order.goodsType = placement.goodsType().code();
        order.sellerUserId = placement.sellerUserId();
        order.buyerUserId = placement.buyerUserId();
        order.quantity = placement.quantity();
        order.unitPriceSnapshot = placement.unitPriceSnapshot();
        order.totalAmount = placement.totalAmount();
        order.deliveryModeSnapshot = placement.deliveryModeSnapshot() == null
                ? null
                : placement.deliveryModeSnapshot().code();
        order.listingTitleSnapshot = placement.listingTitleSnapshot();
        order.status = MarketOrderStatus.ESCROW_PENDING.code();
        order.applyAddressSnapshot(placement.addressSnapshot());
        return order;
    }

    public static MarketOrder reconstitute(MarketOrderSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot must not be null");
        MarketOrderStatus.fromCode(snapshot.status());
        MarketGoodsType.fromCode(snapshot.goodsType());
        if (snapshot.deliveryModeSnapshot() != null) {
            MarketDeliveryMode.fromCode(snapshot.deliveryModeSnapshot());
        }

        MarketOrder order = new MarketOrder();
        order.orderId = snapshot.orderId();
        order.requestId = snapshot.requestId();
        order.listingId = snapshot.listingId();
        order.goodsType = snapshot.goodsType();
        order.sellerUserId = snapshot.sellerUserId();
        order.buyerUserId = snapshot.buyerUserId();
        order.quantity = snapshot.quantity();
        order.unitPriceSnapshot = snapshot.unitPriceSnapshot();
        order.totalAmount = snapshot.totalAmount();
        order.deliveryModeSnapshot = snapshot.deliveryModeSnapshot();
        order.listingTitleSnapshot = snapshot.listingTitleSnapshot();
        order.status = snapshot.status();
        order.escrowTxnId = snapshot.escrowTxnId();
        order.releaseTxnId = snapshot.releaseTxnId();
        order.refundTxnId = snapshot.refundTxnId();
        order.autoConfirmAt = copy(snapshot.autoConfirmAt());
        order.addressIdSnapshot = snapshot.addressIdSnapshot();
        order.receiverNameSnapshot = snapshot.receiverNameSnapshot();
        order.receiverPhoneSnapshot = snapshot.receiverPhoneSnapshot();
        order.provinceSnapshot = snapshot.provinceSnapshot();
        order.citySnapshot = snapshot.citySnapshot();
        order.districtSnapshot = snapshot.districtSnapshot();
        order.detailAddressSnapshot = snapshot.detailAddressSnapshot();
        order.postalCodeSnapshot = snapshot.postalCodeSnapshot();
        order.createTime = copy(snapshot.createTime());
        order.updateTime = copy(snapshot.updateTime());
        return order;
    }

    public MarketOrderStatus status() {
        return MarketOrderStatus.fromCode(status);
    }

    public MarketGoodsType goodsType() {
        return MarketGoodsType.fromCode(goodsType);
    }

    public MarketDeliveryMode deliveryMode() {
        return MarketDeliveryMode.fromCode(deliveryModeSnapshot);
    }

    public void assertReplayMatches(UUID buyerUserId, UUID listingId, int quantity, UUID addressId) {
        if (!Objects.equals(this.buyerUserId, buyerUserId)
                || !Objects.equals(this.listingId, listingId)
                || this.quantity != quantity
                || !addressMatchesReplay(addressId)) {
            throw new BusinessException(
                    MarketErrorCode.REQUEST_REPLAY_CONFLICT,
                    "market order request replay conflict: requestId=" + requestId
            );
        }
    }

    public void assertBuyer(UUID actorUserId) {
        if (!Objects.equals(buyerUserId, actorUserId)) {
            throw new BusinessException(FORBIDDEN, "actor is not market order buyer");
        }
    }

    public void assertSeller(UUID actorUserId) {
        if (!Objects.equals(sellerUserId, actorUserId)) {
            throw new BusinessException(FORBIDDEN, "actor is not market order seller");
        }
    }

    public void assertEscrowed() {
        requireStatus(MarketOrderStatus.ESCROWED);
    }

    public void assertPhysical() {
        if (!goodsType().isPhysical()) {
            throw new BusinessException(INVALID_ARGUMENT, "order goodsType mismatch: orderId=" + orderId);
        }
    }

    public void assertVirtual() {
        if (!goodsType().isVirtual()) {
            throw new BusinessException(INVALID_ARGUMENT, "order goodsType mismatch: orderId=" + orderId);
        }
    }

    public void assertManualDelivery() {
        if (!deliveryMode().isManual()) {
            throw new BusinessException(INVALID_ARGUMENT, "order is not MANUAL delivery: orderId=" + orderId);
        }
    }

    public void assertDisputed() {
        requireStatus(MarketOrderStatus.DISPUTED);
    }

    public boolean isConfirmable() {
        return status().isConfirmable();
    }

    public boolean isDisputable() {
        return status().isDisputable();
    }

    public boolean isEscrowPending() {
        return status() == MarketOrderStatus.ESCROW_PENDING;
    }

    public boolean isEscrowCancelPending() {
        return status() == MarketOrderStatus.ESCROW_CANCEL_PENDING;
    }

    public boolean isPreloadedDelivery() {
        return MarketDeliveryMode.PRELOADED.code().equals(deliveryModeSnapshot);
    }

    public boolean isAutoConfirmDue(Date now) {
        return now != null && isConfirmable() && autoConfirmAt != null && !autoConfirmAt.after(now);
    }

    public MarketOrderTransition recordEscrowSucceeded(UUID escrowTxnId) {
        requireStatus(MarketOrderStatus.ESCROW_PENDING);
        return MarketOrderTransition.escrowSucceeded(orderId, escrowTxnId);
    }

    public MarketOrderTransition recordEscrowFailed() {
        requireStatus(MarketOrderStatus.ESCROW_PENDING);
        return MarketOrderTransition.escrowFailed(orderId);
    }

    public MarketOrderTransition requestEscrowCancel() {
        requireStatus(MarketOrderStatus.ESCROW_PENDING);
        return MarketOrderTransition.escrowCancelPending(orderId);
    }

    public MarketOrderTransition recordLateEscrowSucceeded(UUID escrowTxnId) {
        requireStatus(MarketOrderStatus.ESCROW_CANCEL_PENDING);
        return MarketOrderTransition.lateEscrowRefundPending(orderId, escrowTxnId);
    }

    public MarketOrderTransition cancelWithoutRefund() {
        requireAnyStatus(MarketOrderStatus.ESCROW_CANCEL_PENDING, MarketOrderStatus.ESCROW_FAILED);
        return MarketOrderTransition.cancelledWithoutRefund(orderId);
    }

    public MarketOrderTransition markDelivered(Date autoConfirmAt) {
        requireStatus(MarketOrderStatus.ESCROWED);
        return MarketOrderTransition.delivered(orderId, autoConfirmAt);
    }

    public MarketOrderTransition markShipped(Date autoConfirmAt) {
        requireStatus(MarketOrderStatus.ESCROWED);
        return MarketOrderTransition.shipped(orderId, autoConfirmAt);
    }

    public MarketOrderTransition requestRelease() {
        if (!isConfirmable()) {
            throw new BusinessException(INVALID_ARGUMENT, "order is not confirmable: orderId=" + orderId);
        }
        return MarketOrderTransition.releasePending(orderId);
    }

    public MarketOrderTransition requestRefund() {
        requireStatus(MarketOrderStatus.ESCROWED);
        return MarketOrderTransition.refundPending(orderId);
    }

    public MarketOrderTransition openDispute() {
        if (!isDisputable()) {
            throw new BusinessException(INVALID_ARGUMENT, "order is not disputable: orderId=" + orderId);
        }
        return MarketOrderTransition.disputed(orderId);
    }

    public MarketOrderTransition requestDisputeRefund() {
        requireStatus(MarketOrderStatus.DISPUTED);
        return MarketOrderTransition.disputeRefundPending(orderId);
    }

    public MarketOrderTransition requestDisputeRelease() {
        requireStatus(MarketOrderStatus.DISPUTED);
        return MarketOrderTransition.disputeReleasePending(orderId);
    }

    public MarketOrderTransition recordReleaseSucceeded(UUID releaseTxnId) {
        requireAnyStatus(MarketOrderStatus.RELEASE_PENDING, MarketOrderStatus.DISPUTE_RELEASE_PENDING);
        return MarketOrderTransition.releaseSucceeded(orderId, releaseTxnId);
    }

    public MarketOrderTransition recordRefundSucceeded(UUID refundTxnId) {
        if (status() == MarketOrderStatus.REFUND_PENDING) {
            return MarketOrderTransition.refundSucceeded(orderId, refundTxnId);
        }
        if (status() == MarketOrderStatus.DISPUTE_REFUND_PENDING) {
            return MarketOrderTransition.disputeRefundSucceeded(orderId, refundTxnId);
        }
        throw statusMismatch();
    }

    public String pendingWalletActionType() {
        return status().pendingWalletActionType();
    }

    private void applyAddressSnapshot(MarketAddressSnapshot snapshot) {
        if (snapshot == null) {
            return;
        }
        addressIdSnapshot = snapshot.addressId();
        receiverNameSnapshot = snapshot.receiverName();
        receiverPhoneSnapshot = snapshot.receiverPhone();
        provinceSnapshot = snapshot.province();
        citySnapshot = snapshot.city();
        districtSnapshot = snapshot.district();
        detailAddressSnapshot = snapshot.detailAddress();
        postalCodeSnapshot = snapshot.postalCode();
    }

    private boolean addressMatchesReplay(UUID addressId) {
        if (!goodsType().isPhysical()) {
            return true;
        }
        return addressIdSnapshot != null && Objects.equals(addressIdSnapshot, addressId);
    }

    private void requireStatus(MarketOrderStatus expectedStatus) {
        if (status() != expectedStatus) {
            throw statusMismatch();
        }
    }

    private void requireAnyStatus(MarketOrderStatus first, MarketOrderStatus second) {
        MarketOrderStatus current = status();
        if (current != first && current != second) {
            throw statusMismatch();
        }
    }

    private BusinessException statusMismatch() {
        return new BusinessException(INVALID_ARGUMENT, "order status mismatch: orderId=" + orderId);
    }

    private static Date copy(Date value) {
        return value == null ? null : new Date(value.getTime());
    }

    public UUID getOrderId() {
        return orderId;
    }

    public String getRequestId() {
        return requestId;
    }

    public UUID getListingId() {
        return listingId;
    }

    public String getGoodsType() {
        return goodsType;
    }

    public UUID getSellerUserId() {
        return sellerUserId;
    }

    public UUID getBuyerUserId() {
        return buyerUserId;
    }

    public int getQuantity() {
        return quantity;
    }

    public long getUnitPriceSnapshot() {
        return unitPriceSnapshot;
    }

    public long getTotalAmount() {
        return totalAmount;
    }

    public String getDeliveryModeSnapshot() {
        return deliveryModeSnapshot;
    }

    public String getListingTitleSnapshot() {
        return listingTitleSnapshot;
    }

    public String getStatus() {
        return status;
    }

    public UUID getEscrowTxnId() {
        return escrowTxnId;
    }

    public UUID getReleaseTxnId() {
        return releaseTxnId;
    }

    public UUID getRefundTxnId() {
        return refundTxnId;
    }

    public Date getAutoConfirmAt() {
        return copy(autoConfirmAt);
    }

    public UUID getAddressIdSnapshot() {
        return addressIdSnapshot;
    }

    public String getReceiverNameSnapshot() {
        return receiverNameSnapshot;
    }

    public String getReceiverPhoneSnapshot() {
        return receiverPhoneSnapshot;
    }

    public String getProvinceSnapshot() {
        return provinceSnapshot;
    }

    public String getCitySnapshot() {
        return citySnapshot;
    }

    public String getDistrictSnapshot() {
        return districtSnapshot;
    }

    public String getDetailAddressSnapshot() {
        return detailAddressSnapshot;
    }

    public String getPostalCodeSnapshot() {
        return postalCodeSnapshot;
    }

    public Date getCreateTime() {
        return copy(createTime);
    }

    public Date getUpdateTime() {
        return copy(updateTime);
    }
}
