package com.nowcoder.community.market.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record AddMarketInventoryBatchRequest(
        @NotBlank String payloadType,
        @NotEmpty List<@NotBlank String> payloads
) {
}
