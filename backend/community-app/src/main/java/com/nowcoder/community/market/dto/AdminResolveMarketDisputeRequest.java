package com.nowcoder.community.market.dto;

import jakarta.validation.constraints.NotBlank;

public class AdminResolveMarketDisputeRequest {

    private String resolutionType;

    @NotBlank
    private String note;

    public String getResolutionType() {
        return resolutionType;
    }

    public void setResolutionType(String resolutionType) {
        this.resolutionType = resolutionType;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
