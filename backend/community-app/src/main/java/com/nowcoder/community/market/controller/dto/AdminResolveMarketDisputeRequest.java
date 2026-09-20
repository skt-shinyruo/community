package com.nowcoder.community.market.controller.dto;

import jakarta.validation.constraints.NotBlank;

public record AdminResolveMarketDisputeRequest(
        @NotBlank String note
) {
}
