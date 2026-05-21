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

    public MarketOrderStatus status() {
        return MarketOrderStatus.fromCode(status);
    }

    public MarketGoodsType goodsType() {
        return MarketGoodsType.fromCode(goodsType);
    }

    public MarketDeliveryMode deliveryMode() {
        return MarketDeliveryMode.fromCode(deliveryModeSnapshot);
    }

    public void assertReplayMatches(
            UUID buyerUserId,
            UUID listingId,
            int quantity,
            UUID addressId,
            MarketAddressSnapshot suppliedAddressSnapshot
    ) {
        if (!Objects.equals(this.buyerUserId, buyerUserId)
                || !Objects.equals(this.listingId, listingId)
                || this.quantity != quantity
                || !addressMatchesReplay(addressId, suppliedAddressSnapshot)) {
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

    public MarketOrderTransition requestEscrowCancel() {
        requireStatus(MarketOrderStatus.ESCROW_PENDING);
        return MarketOrderTransition.escrowCancelPending(orderId);
    }

    public MarketOrderTransition openDispute() {
        if (!isDisputable()) {
            throw new BusinessException(INVALID_ARGUMENT, "order is not disputable: orderId=" + orderId);
        }
        return MarketOrderTransition.disputed(orderId);
    }

    public void assertDisputed() {
        requireStatus(MarketOrderStatus.DISPUTED);
    }

    public MarketOrderTransition requestDisputeRefund() {
        requireStatus(MarketOrderStatus.DISPUTED);
        return MarketOrderTransition.disputeRefundPending(orderId);
    }

    public MarketOrderTransition requestDisputeRelease() {
        requireStatus(MarketOrderStatus.DISPUTED);
        return MarketOrderTransition.disputeReleasePending(orderId);
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

    private boolean addressMatchesReplay(UUID addressId, MarketAddressSnapshot suppliedAddressSnapshot) {
        if (!goodsType().isPhysical()) {
            return true;
        }
        if (addressIdSnapshot != null) {
            return Objects.equals(addressIdSnapshot, addressId);
        }
        if (suppliedAddressSnapshot == null) {
            return false;
        }
        return Objects.equals(addressId, suppliedAddressSnapshot.addressId())
                && Objects.equals(receiverNameSnapshot, suppliedAddressSnapshot.receiverName())
                && Objects.equals(receiverPhoneSnapshot, suppliedAddressSnapshot.receiverPhone())
                && Objects.equals(provinceSnapshot, suppliedAddressSnapshot.province())
                && Objects.equals(citySnapshot, suppliedAddressSnapshot.city())
                && Objects.equals(districtSnapshot, suppliedAddressSnapshot.district())
                && Objects.equals(detailAddressSnapshot, suppliedAddressSnapshot.detailAddress())
                && Objects.equals(postalCodeSnapshot, suppliedAddressSnapshot.postalCode());
    }

    private void requireStatus(MarketOrderStatus expectedStatus) {
        if (status() != expectedStatus) {
            throw new BusinessException(INVALID_ARGUMENT, "order status mismatch: orderId=" + orderId);
        }
    }

    public UUID getOrderId() {
        return orderId;
    }

    public void setOrderId(UUID orderId) {
        this.orderId = orderId;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public UUID getListingId() {
        return listingId;
    }

    public void setListingId(UUID listingId) {
        this.listingId = listingId;
    }

    public String getGoodsType() {
        return goodsType;
    }

    public void setGoodsType(String goodsType) {
        this.goodsType = goodsType;
    }

    public UUID getSellerUserId() {
        return sellerUserId;
    }

    public void setSellerUserId(UUID sellerUserId) {
        this.sellerUserId = sellerUserId;
    }

    public UUID getBuyerUserId() {
        return buyerUserId;
    }

    public void setBuyerUserId(UUID buyerUserId) {
        this.buyerUserId = buyerUserId;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public long getUnitPriceSnapshot() {
        return unitPriceSnapshot;
    }

    public void setUnitPriceSnapshot(long unitPriceSnapshot) {
        this.unitPriceSnapshot = unitPriceSnapshot;
    }

    public long getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(long totalAmount) {
        this.totalAmount = totalAmount;
    }

    public String getDeliveryModeSnapshot() {
        return deliveryModeSnapshot;
    }

    public void setDeliveryModeSnapshot(String deliveryModeSnapshot) {
        this.deliveryModeSnapshot = deliveryModeSnapshot;
    }

    public String getListingTitleSnapshot() {
        return listingTitleSnapshot;
    }

    public void setListingTitleSnapshot(String listingTitleSnapshot) {
        this.listingTitleSnapshot = listingTitleSnapshot;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public UUID getEscrowTxnId() {
        return escrowTxnId;
    }

    public void setEscrowTxnId(UUID escrowTxnId) {
        this.escrowTxnId = escrowTxnId;
    }

    public UUID getReleaseTxnId() {
        return releaseTxnId;
    }

    public void setReleaseTxnId(UUID releaseTxnId) {
        this.releaseTxnId = releaseTxnId;
    }

    public UUID getRefundTxnId() {
        return refundTxnId;
    }

    public void setRefundTxnId(UUID refundTxnId) {
        this.refundTxnId = refundTxnId;
    }

    public Date getAutoConfirmAt() {
        return autoConfirmAt;
    }

    public void setAutoConfirmAt(Date autoConfirmAt) {
        this.autoConfirmAt = autoConfirmAt;
    }

    public UUID getAddressIdSnapshot() {
        return addressIdSnapshot;
    }

    public void setAddressIdSnapshot(UUID addressIdSnapshot) {
        this.addressIdSnapshot = addressIdSnapshot;
    }

    public String getReceiverNameSnapshot() {
        return receiverNameSnapshot;
    }

    public void setReceiverNameSnapshot(String receiverNameSnapshot) {
        this.receiverNameSnapshot = receiverNameSnapshot;
    }

    public String getReceiverPhoneSnapshot() {
        return receiverPhoneSnapshot;
    }

    public void setReceiverPhoneSnapshot(String receiverPhoneSnapshot) {
        this.receiverPhoneSnapshot = receiverPhoneSnapshot;
    }

    public String getProvinceSnapshot() {
        return provinceSnapshot;
    }

    public void setProvinceSnapshot(String provinceSnapshot) {
        this.provinceSnapshot = provinceSnapshot;
    }

    public String getCitySnapshot() {
        return citySnapshot;
    }

    public void setCitySnapshot(String citySnapshot) {
        this.citySnapshot = citySnapshot;
    }

    public String getDistrictSnapshot() {
        return districtSnapshot;
    }

    public void setDistrictSnapshot(String districtSnapshot) {
        this.districtSnapshot = districtSnapshot;
    }

    public String getDetailAddressSnapshot() {
        return detailAddressSnapshot;
    }

    public void setDetailAddressSnapshot(String detailAddressSnapshot) {
        this.detailAddressSnapshot = detailAddressSnapshot;
    }

    public String getPostalCodeSnapshot() {
        return postalCodeSnapshot;
    }

    public void setPostalCodeSnapshot(String postalCodeSnapshot) {
        this.postalCodeSnapshot = postalCodeSnapshot;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    public Date getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime) {
        this.updateTime = updateTime;
    }
}
