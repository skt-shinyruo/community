package com.nowcoder.community.analytics.controller.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public class RangeQuery {

    @NotNull
    private LocalDate start;

    @NotNull
    private LocalDate end;

    public LocalDate getStart() {
        return start;
    }

    public void setStart(LocalDate start) {
        this.start = start;
    }

    public LocalDate getEnd() {
        return end;
    }

    public void setEnd(LocalDate end) {
        this.end = end;
    }
}
