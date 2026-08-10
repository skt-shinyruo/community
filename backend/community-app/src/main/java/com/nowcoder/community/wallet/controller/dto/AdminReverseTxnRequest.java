package com.nowcoder.community.wallet.controller.dto;

import jakarta.validation.constraints.NotBlank;

public record AdminReverseTxnRequest(
        @NotBlank String txnRef,
        @NotBlank String reason
) {
}
