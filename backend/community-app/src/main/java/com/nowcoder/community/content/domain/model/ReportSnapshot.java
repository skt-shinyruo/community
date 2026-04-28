package com.nowcoder.community.content.domain.model;

import java.util.Date;
import java.util.UUID;

public record ReportSnapshot(
        UUID id,
        UUID reporterId,
        int targetType,
        UUID targetId,
        String reason,
        String detail,
        int status,
        Date createTime
) {
}
