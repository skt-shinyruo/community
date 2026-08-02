package com.nowcoder.community.content.domain.model;

import java.util.Date;
import java.util.UUID;

public record PostSnapshot(
        UUID id,
        UUID userId,
        int type,
        int status,
        Date createTime,
        Date updateTime,
        long aggregateVersion
) {

    public boolean isDeleted() {
        return DiscussPost.isDeletedStatus(status);
    }

    public boolean isActive() {
        return !isDeleted();
    }
}
