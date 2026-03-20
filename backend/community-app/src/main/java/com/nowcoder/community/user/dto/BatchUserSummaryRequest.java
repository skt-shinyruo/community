package com.nowcoder.community.user.dto;

import jakarta.validation.constraints.Size;

import java.util.List;

public class BatchUserSummaryRequest {

    /**
     * 用户 ID 列表（建议去重，最大 200）。
     */
    @Size(max = 200, message = "userIds 过多（max=200）")
    private List<Integer> userIds;

    public List<Integer> getUserIds() {
        return userIds;
    }

    public void setUserIds(List<Integer> userIds) {
        this.userIds = userIds;
    }
}

