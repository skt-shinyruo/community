package com.nowcoder.community.market.support;

import com.nowcoder.community.market.domain.model.MarketOrder;
import com.nowcoder.community.market.domain.model.MarketOrderSnapshot;

import java.util.Date;
import java.util.Objects;
import java.util.UUID;

public final class MarketOrderTestFixture {

    private static final UUID DEFAULT_LISTING_ID = uuid(9_001);
    private static final UUID DEFAULT_SELLER_ID = uuid(9_002);
    private static final UUID DEFAULT_BUYER_ID = uuid(9_003);

    private MarketOrderTestFixture() {
    }

    public static Builder order(UUID orderId) {
        return new Builder(Objects.requireNonNull(orderId, "orderId must not be null"));
    }

    public static Builder order(MarketOrder source) {
        Objects.requireNonNull(source, "source must not be null");
        return order(source.getOrderId())
                .requestId(source.getRequestId())
                .listingId(source.getListingId())
                .goodsType(source.getGoodsType())
                .sellerUserId(source.getSellerUserId())
                .buyerUserId(source.getBuyerUserId())
                .quantity(source.getQuantity())
                .unitPriceSnapshot(source.getUnitPriceSnapshot())
                .totalAmount(source.getTotalAmount())
                .deliveryModeSnapshot(source.getDeliveryModeSnapshot())
                .listingTitleSnapshot(source.getListingTitleSnapshot())
                .status(source.getStatus())
                .escrowTxnId(source.getEscrowTxnId())
                .releaseTxnId(source.getReleaseTxnId())
                .refundTxnId(source.getRefundTxnId())
                .autoConfirmAt(source.getAutoConfirmAt())
                .addressSnapshot(
                        source.getAddressIdSnapshot(),
                        source.getReceiverNameSnapshot(),
                        source.getReceiverPhoneSnapshot(),
                        source.getProvinceSnapshot(),
                        source.getCitySnapshot(),
                        source.getDistrictSnapshot(),
                        source.getDetailAddressSnapshot(),
                        source.getPostalCodeSnapshot()
                )
                .createTime(source.getCreateTime())
                .updateTime(source.getUpdateTime());
    }

    public static final class Builder {

        private final UUID orderId;
        private String requestId;
        private UUID listingId = DEFAULT_LISTING_ID;
        private String goodsType = "PHYSICAL";
        private UUID sellerUserId = DEFAULT_SELLER_ID;
        private UUID buyerUserId = DEFAULT_BUYER_ID;
        private int quantity = 1;
        private long unitPriceSnapshot;
        private long totalAmount;
        private String deliveryModeSnapshot = "MANUAL";
        private String listingTitleSnapshot = "Test market order";
        private String status = "ESCROW_PENDING";
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

        private Builder(UUID orderId) {
            this.orderId = orderId;
            this.requestId = "test:market-order:" + orderId;
        }

        public Builder requestId(String requestId) {
            this.requestId = requestId;
            return this;
        }

        public Builder listingId(UUID listingId) {
            this.listingId = listingId;
            return this;
        }

        public Builder goodsType(String goodsType) {
            this.goodsType = goodsType;
            return this;
        }

        public Builder sellerUserId(UUID sellerUserId) {
            this.sellerUserId = sellerUserId;
            return this;
        }

        public Builder buyerUserId(UUID buyerUserId) {
            this.buyerUserId = buyerUserId;
            return this;
        }

        public Builder quantity(int quantity) {
            this.quantity = quantity;
            return this;
        }

        public Builder unitPriceSnapshot(long unitPriceSnapshot) {
            this.unitPriceSnapshot = unitPriceSnapshot;
            return this;
        }

        public Builder totalAmount(long totalAmount) {
            this.totalAmount = totalAmount;
            return this;
        }

        public Builder deliveryModeSnapshot(String deliveryModeSnapshot) {
            this.deliveryModeSnapshot = deliveryModeSnapshot;
            return this;
        }

        public Builder listingTitleSnapshot(String listingTitleSnapshot) {
            this.listingTitleSnapshot = listingTitleSnapshot;
            return this;
        }

        public Builder status(String status) {
            this.status = status;
            return this;
        }

        public Builder escrowTxnId(UUID escrowTxnId) {
            this.escrowTxnId = escrowTxnId;
            return this;
        }

        public Builder releaseTxnId(UUID releaseTxnId) {
            this.releaseTxnId = releaseTxnId;
            return this;
        }

        public Builder refundTxnId(UUID refundTxnId) {
            this.refundTxnId = refundTxnId;
            return this;
        }

        public Builder autoConfirmAt(Date autoConfirmAt) {
            this.autoConfirmAt = copy(autoConfirmAt);
            return this;
        }

        public Builder addressIdSnapshot(UUID addressIdSnapshot) {
            this.addressIdSnapshot = addressIdSnapshot;
            return this;
        }

        public Builder addressSnapshot(
                UUID addressIdSnapshot,
                String receiverNameSnapshot,
                String receiverPhoneSnapshot,
                String provinceSnapshot,
                String citySnapshot,
                String districtSnapshot,
                String detailAddressSnapshot,
                String postalCodeSnapshot
        ) {
            this.addressIdSnapshot = addressIdSnapshot;
            this.receiverNameSnapshot = receiverNameSnapshot;
            this.receiverPhoneSnapshot = receiverPhoneSnapshot;
            this.provinceSnapshot = provinceSnapshot;
            this.citySnapshot = citySnapshot;
            this.districtSnapshot = districtSnapshot;
            this.detailAddressSnapshot = detailAddressSnapshot;
            this.postalCodeSnapshot = postalCodeSnapshot;
            return this;
        }

        public Builder createTime(Date createTime) {
            this.createTime = copy(createTime);
            return this;
        }

        public Builder updateTime(Date updateTime) {
            this.updateTime = copy(updateTime);
            return this;
        }

        public MarketOrder build() {
            return MarketOrder.reconstitute(new MarketOrderSnapshot(
                    orderId,
                    requestId,
                    listingId,
                    goodsType,
                    sellerUserId,
                    buyerUserId,
                    quantity,
                    unitPriceSnapshot,
                    totalAmount,
                    deliveryModeSnapshot,
                    listingTitleSnapshot,
                    status,
                    escrowTxnId,
                    releaseTxnId,
                    refundTxnId,
                    autoConfirmAt,
                    addressIdSnapshot,
                    receiverNameSnapshot,
                    receiverPhoneSnapshot,
                    provinceSnapshot,
                    citySnapshot,
                    districtSnapshot,
                    detailAddressSnapshot,
                    postalCodeSnapshot,
                    createTime,
                    updateTime
            ));
        }
    }

    private static UUID uuid(long value) {
        return UUID.fromString(String.format("00000000-0000-7000-8000-%012d", value));
    }

    private static Date copy(Date value) {
        return value == null ? null : new Date(value.getTime());
    }
}
