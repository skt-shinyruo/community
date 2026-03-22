package com.nowcoder.community.content.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

public class BatchPostSummaryRequest {

    @NotNull
    private List<Integer> postIds;

    public List<Integer> getPostIds() {
        return postIds;
    }

    public void setPostIds(List<Integer> postIds) {
        this.postIds = postIds;
    }
}
