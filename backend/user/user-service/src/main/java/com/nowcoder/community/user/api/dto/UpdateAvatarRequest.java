package com.nowcoder.community.user.api.dto;

import jakarta.validation.constraints.NotBlank;

public class UpdateAvatarRequest {

    @NotBlank
    private String fileName;

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }
}

