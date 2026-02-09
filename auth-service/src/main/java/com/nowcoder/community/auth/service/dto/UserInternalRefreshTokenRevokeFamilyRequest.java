package com.nowcoder.community.auth.service.dto;

public class UserInternalRefreshTokenRevokeFamilyRequest {

    private String familyId;

    public String getFamilyId() {
        return familyId;
    }

    public void setFamilyId(String familyId) {
        this.familyId = familyId;
    }
}

