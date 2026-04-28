package com.nowcoder.community.content.domain.model;

import java.util.Date;
import java.util.UUID;

public record PostSnapshot(
        UUID id,
        UUID userId,
        int status,
        Date createTime
) {
}
