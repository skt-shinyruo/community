package com.nowcoder.community.market.controller.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateMarketDisputeRequest(
        @NotBlank String reason,
        @NotBlank String buyerNote
) {
}
