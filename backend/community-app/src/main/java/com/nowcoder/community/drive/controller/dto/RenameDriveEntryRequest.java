package com.nowcoder.community.drive.controller.dto;

import jakarta.validation.constraints.NotBlank;

public record RenameDriveEntryRequest(@NotBlank String newName) {
}
