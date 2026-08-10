package com.nowcoder.community.drive.controller.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateDriveFolderRequest(String parentId, @NotBlank String name) {
}
