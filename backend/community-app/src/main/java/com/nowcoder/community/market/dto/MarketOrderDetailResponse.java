package com.nowcoder.community.market.dto;

import com.nowcoder.community.market.entity.MarketOrder;
import com.nowcoder.community.market.entity.MarketShipment;

import java.util.Date;
import java.util.List;
import java.util.UUID;

public record MarketOrderDetailResponse(
        UUID orderId,
        String requestId,
        UUID listingId,
        String goodsType,
        UUID sellerUserId,
        UUID buyerUserId,
        int quantity,
        long unitPriceSnapshot,
        long totalAmount,
        String deliveryModeSnapshot,
        String listingTitleSnapshot,
        String status,
        UUID escrowTxnId,
        UUID releaseTxnId,
        UUID refundTxnId,
        Date autoConfirmAt,
        String receiverNameSnapshot,
        String receiverPhoneSnapshot,
        String provinceSnapshot,
        String citySnapshot,
        String districtSnapshot,
        String detailAddressSnapshot,
        String postalCodeSnapshot,
        List<String> deliveryContents,
        ShipmentView shipment,
        Date createTime,
        Date updateTime
) {

    public static MarketOrderDetailResponse from(MarketOrder order,
                                                 List<String> deliveryContents,
                                                 MarketShipment shipment) {
        return new MarketOrderDetailResponse(
                order.getOrderId(),
                order.getRequestId(),
                order.getListingId(),
                order.getGoodsType(),
                order.getSellerUserId(),
                order.getBuyerUserId(),
                order.getQuantity(),
                order.getUnitPriceSnapshot(),
                order.getTotalAmount(),
                order.getDeliveryModeSnapshot(),
                order.getListingTitleSnapshot(),
                order.getStatus(),
                order.getEscrowTxnId(),
                order.getReleaseTxnId(),
                order.getRefundTxnId(),
                order.getAutoConfirmAt(),
                order.getReceiverNameSnapshot(),
                order.getReceiverPhoneSnapshot(),
                order.getProvinceSnapshot(),
                order.getCitySnapshot(),
                order.getDistrictSnapshot(),
                order.getDetailAddressSnapshot(),
                order.getPostalCodeSnapshot(),
                deliveryContents == null ? List.of() : List.copyOf(deliveryContents),
                ShipmentView.from(shipment),
                order.getCreateTime(),
                order.getUpdateTime()
        );
    }

    public record ShipmentView(
            UUID shipmentId,
            UUID orderId,
            UUID sellerUserId,
            String carrierName,
            String trackingNo,
            String shippingRemark,
            Date shippedAt,
            Date createTime,
            Date updateTime
    ) {

        public static ShipmentView from(MarketShipment shipment) {
            if (shipment == null) {
                return null;
            }
            return new ShipmentView(
                    shipment.getShipmentId(),
                    shipment.getOrderId(),
                    shipment.getSellerUserId(),
                    shipment.getCarrierName(),
                    shipment.getTrackingNo(),
                    shipment.getShippingRemark(),
                    shipment.getShippedAt(),
                    shipment.getCreateTime(),
                    shipment.getUpdateTime()
            );
        }
    }
}
