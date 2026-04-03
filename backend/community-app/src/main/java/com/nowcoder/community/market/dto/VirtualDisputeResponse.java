package com.nowcoder.community.market.dto;

import com.nowcoder.community.market.entity.VirtualDispute;

import java.util.Date;

public record VirtualDisputeResponse(
        long disputeId,
        long orderId,
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

    public static VirtualDisputeResponse from(VirtualDispute dispute) {
        return new VirtualDisputeResponse(
                dispute.getDisputeId(),
                dispute.getOrderId(),
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
