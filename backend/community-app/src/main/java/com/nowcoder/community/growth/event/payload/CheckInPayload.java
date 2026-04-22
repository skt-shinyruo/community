package com.nowcoder.community.growth.event.payload;

import java.time.LocalDate;
import java.util.UUID;

public class CheckInPayload {

    private UUID userId;
    private LocalDate bizDate;

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public LocalDate getBizDate() {
        return bizDate;
    }

    public void setBizDate(LocalDate bizDate) {
        this.bizDate = bizDate;
    }
}
