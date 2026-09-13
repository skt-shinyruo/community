package com.nowcoder.community.market.controller.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record AddMarketInventoryBatchRequest(
        @NotBlank String payloadType,
        @NotEmpty List<@NotBlank String> payloads
) {

    @JsonAnySetter
    public void rejectUnknown(String name, Object value) {
        throw new IllegalArgumentException("Unknown market inventory request field: " + name);
    }
}
