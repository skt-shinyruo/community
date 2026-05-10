package com.nowcoder.community.drive.controller.dto;

import jakarta.validation.constraints.NotBlank;

public class RenameDriveEntryRequest {

    @NotBlank
    private String newName;

    public String getNewName() {
        return newName;
    }

    public void setNewName(String newName) {
        this.newName = newName;
    }
}
