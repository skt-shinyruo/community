package com.nowcoder.community.user.dto;

import jakarta.validation.constraints.NotBlank;

public class InternalRefreshTokenRevokeFamilyRequest {

    @NotBlank
    private String familyId;

    public String getFamilyId() {
        return familyId;
    }

    public void setFamilyId(String familyId) {
        this.familyId = familyId;
    }
}

