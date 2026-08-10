package com.nowcoder.community.wallet.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AdminFreezeWalletRequest(
        @NotNull UUID userId,
        @NotBlank String reason
) {
}
