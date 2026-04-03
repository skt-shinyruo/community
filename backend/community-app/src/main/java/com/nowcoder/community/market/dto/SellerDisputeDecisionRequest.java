package com.nowcoder.community.market.dto;

import jakarta.validation.constraints.NotBlank;

public class SellerDisputeDecisionRequest {

    @NotBlank
    private String note;

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
