package com.nowcoder.community.market.controller.dto;

import com.nowcoder.community.market.application.result.MarketOrderDetailResult;
import com.nowcoder.community.market.application.result.MarketShipmentResult;

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

    public static MarketOrderDetailResponse from(MarketOrderDetailResult order) {
        return new MarketOrderDetailResponse(
                order.orderId(),
                order.requestId(),
                order.listingId(),
                order.goodsType(),
                order.sellerUserId(),
                order.buyerUserId(),
                order.quantity(),
                order.unitPriceSnapshot(),
                order.totalAmount(),
                order.deliveryModeSnapshot(),
                order.listingTitleSnapshot(),
                order.status(),
                order.escrowTxnId(),
                order.releaseTxnId(),
                order.refundTxnId(),
                order.autoConfirmAt(),
                order.receiverNameSnapshot(),
                order.receiverPhoneSnapshot(),
                order.provinceSnapshot(),
                order.citySnapshot(),
                order.districtSnapshot(),
                order.detailAddressSnapshot(),
                order.postalCodeSnapshot(),
                order.deliveryContents(),
                ShipmentView.from(order.shipment()),
                order.createTime(),
                order.updateTime()
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

        public static ShipmentView from(MarketShipmentResult shipment) {
            if (shipment == null) {
                return null;
            }
            return new ShipmentView(
                    shipment.shipmentId(),
                    shipment.orderId(),
                    shipment.sellerUserId(),
                    shipment.carrierName(),
                    shipment.trackingNo(),
                    shipment.shippingRemark(),
                    shipment.shippedAt(),
                    shipment.createTime(),
                    shipment.updateTime()
            );
        }
    }
}
