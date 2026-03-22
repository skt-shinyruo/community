package com.nowcoder.community.growth.dto;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class TaskCenterResponse {

    private LocalDate bizDate;
    private List<TaskItemResponse> items = new ArrayList<>();

    public LocalDate getBizDate() {
        return bizDate;
    }

    public void setBizDate(LocalDate bizDate) {
        this.bizDate = bizDate;
    }

    public List<TaskItemResponse> getItems() {
        return items;
    }

    public void setItems(List<TaskItemResponse> items) {
        this.items = items == null ? new ArrayList<>() : items;
    }
}
