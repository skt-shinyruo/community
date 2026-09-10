package com.nowcoder.community.market.controller.dto;

public record AdminResolveMarketDisputeRequest(
        String resolutionType,
        String note
) {
}
