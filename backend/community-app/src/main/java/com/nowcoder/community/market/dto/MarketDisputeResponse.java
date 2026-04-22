package com.nowcoder.community.market.dto;

import com.nowcoder.community.market.entity.MarketDispute;

import java.util.Date;
import java.util.UUID;

public record MarketDisputeResponse(
        UUID disputeId,
        UUID orderId,
        String goodsType,
        UUID buyerUserId,
        UUID sellerUserId,
        String status,
        String reason,
        String buyerNote,
        String sellerNote,
        String resolutionType,
        UUID resolvedBy,
        Date resolvedAt,
        Date createTime,
        Date updateTime
) {

    public static MarketDisputeResponse from(MarketDispute dispute) {
        return new MarketDisputeResponse(
                dispute.getDisputeId(),
                dispute.getOrderId(),
                dispute.getGoodsType(),
                dispute.getBuyerUserId(),
                dispute.getSellerUserId(),
                dispute.getStatus(),
                dispute.getReason(),
                dispute.getBuyerNote(),
                dispute.getSellerNote(),
                dispute.getResolutionType(),
                dispute.getResolvedBy(),
                dispute.getResolvedAt(),
                dispute.getCreateTime(),
                dispute.getUpdateTime()
        );
    }
}
