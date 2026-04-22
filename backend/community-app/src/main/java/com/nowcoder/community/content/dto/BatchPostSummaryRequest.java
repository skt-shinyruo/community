package com.nowcoder.community.content.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public class BatchPostSummaryRequest {

    @NotNull
    private List<UUID> postIds;

    public List<UUID> getPostIds() {
        return postIds;
    }

    public void setPostIds(List<UUID> postIds) {
        this.postIds = postIds;
    }
}
