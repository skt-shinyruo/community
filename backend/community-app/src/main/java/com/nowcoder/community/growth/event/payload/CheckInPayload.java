package com.nowcoder.community.growth.event.payload;

import java.time.LocalDate;

public class CheckInPayload {

    private int userId;
    private LocalDate bizDate;

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public LocalDate getBizDate() {
        return bizDate;
    }

    public void setBizDate(LocalDate bizDate) {
        this.bizDate = bizDate;
    }
}
