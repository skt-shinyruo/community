package com.nowcoder.community.market.application.result;

import com.nowcoder.community.market.domain.model.MarketDispute;

import java.util.Date;
import java.util.UUID;

public record MarketDisputeResult(
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

    public static MarketDisputeResult from(MarketDispute dispute) {
        return new MarketDisputeResult(
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
