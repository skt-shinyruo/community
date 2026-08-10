package com.nowcoder.community.drive.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record CreateDriveShareRequest(
        @NotBlank String password,
        @NotNull Instant expiresAt
) {
}
