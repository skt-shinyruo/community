package com.nowcoder.community.market.dto;

import com.nowcoder.community.market.model.MarketDisputeResult;

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

    public static MarketDisputeResponse from(MarketDisputeResult dispute) {
        return new MarketDisputeResponse(
                dispute.disputeId(),
                dispute.orderId(),
                dispute.goodsType(),
                dispute.buyerUserId(),
                dispute.sellerUserId(),
                dispute.status(),
                dispute.reason(),
                dispute.buyerNote(),
                dispute.sellerNote(),
                dispute.resolutionType(),
                dispute.resolvedBy(),
                dispute.resolvedAt(),
                dispute.createTime(),
                dispute.updateTime()
        );
    }
}
