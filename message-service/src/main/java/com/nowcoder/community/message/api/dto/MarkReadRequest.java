package com.nowcoder.community.message.api.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public class MarkReadRequest {

    @NotEmpty
    private List<Integer> ids;

    public List<Integer> getIds() {
        return ids;
    }

    public void setIds(List<Integer> ids) {
        this.ids = ids;
    }
}

