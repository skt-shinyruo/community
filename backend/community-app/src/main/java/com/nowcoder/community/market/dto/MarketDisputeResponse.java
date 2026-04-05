package com.nowcoder.community.market.dto;

import com.nowcoder.community.market.entity.MarketDispute;

import java.util.Date;

public record MarketDisputeResponse(
        long disputeId,
        long orderId,
        String goodsType,
        int buyerUserId,
        int sellerUserId,
        String status,
        String reason,
        String buyerNote,
        String sellerNote,
        String resolutionType,
        Integer resolvedBy,
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
