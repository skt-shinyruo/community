package com.nowcoder.community.ops.controller.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public class HotCachePrewarmRequest {

    private String scope = "global";

    private UUID boardId;

    @Min(1)
    @Max(500)
    private int limit = 50;

    @NotBlank
    private String reason;

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    public UUID getBoardId() {
        return boardId;
    }

    public void setBoardId(UUID boardId) {
        this.boardId = boardId;
    }

    public int getLimit() {
        return limit;
    }

    public void setLimit(int limit) {
        this.limit = limit;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    @AssertTrue(message = "boardId is required for board scope")
    public boolean isBoardScopeValid() {
        return !"board".equals(scope) || boardId != null;
    }
}
