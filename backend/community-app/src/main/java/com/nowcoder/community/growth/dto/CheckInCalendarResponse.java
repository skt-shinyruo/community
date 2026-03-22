package com.nowcoder.community.growth.dto;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class CheckInCalendarResponse {

    private int year;
    private int month;
    private List<LocalDate> checkedInDates = new ArrayList<>();

    public int getYear() {
        return year;
    }

    public void setYear(int year) {
        this.year = year;
    }

    public int getMonth() {
        return month;
    }

    public void setMonth(int month) {
        this.month = month;
    }

    public List<LocalDate> getCheckedInDates() {
        return checkedInDates;
    }

    public void setCheckedInDates(List<LocalDate> checkedInDates) {
        this.checkedInDates = checkedInDates;
    }
}
