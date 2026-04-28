package com.nowcoder.community.market.controller.dto;

import jakarta.validation.constraints.NotBlank;

public class CreateMarketDisputeRequest {

    @NotBlank
    private String reason;

    @NotBlank
    private String buyerNote;

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getBuyerNote() {
        return buyerNote;
    }

    public void setBuyerNote(String buyerNote) {
        this.buyerNote = buyerNote;
    }
}
