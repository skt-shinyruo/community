package com.nowcoder.community.market.dto;

import jakarta.validation.constraints.NotBlank;

public class AdminResolveVirtualDisputeRequest {

    @NotBlank
    private String note;

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
