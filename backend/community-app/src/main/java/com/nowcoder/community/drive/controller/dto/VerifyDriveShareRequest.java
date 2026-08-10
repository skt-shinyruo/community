package com.nowcoder.community.drive.controller.dto;

import jakarta.validation.constraints.NotBlank;

public record VerifyDriveShareRequest(@NotBlank String password) {
}
