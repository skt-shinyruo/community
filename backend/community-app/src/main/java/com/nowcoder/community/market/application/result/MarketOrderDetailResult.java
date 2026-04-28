package com.nowcoder.community.market.application.result;

import com.nowcoder.community.market.domain.model.MarketOrder;
import com.nowcoder.community.market.domain.model.MarketShipment;

import java.util.Date;
import java.util.List;
import java.util.UUID;

public record MarketOrderDetailResult(
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
        MarketShipmentResult shipment,
        Date createTime,
        Date updateTime
) {

    public static MarketOrderDetailResult from(MarketOrder order,
                                             List<String> deliveryContents,
                                             MarketShipment shipment) {
        return new MarketOrderDetailResult(
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
                MarketShipmentResult.from(shipment),
                order.getCreateTime(),
                order.getUpdateTime()
        );
    }
}
