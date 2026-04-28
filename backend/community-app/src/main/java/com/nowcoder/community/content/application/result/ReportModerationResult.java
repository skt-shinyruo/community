package com.nowcoder.community.content.application.result;

import java.util.Date;
import java.util.UUID;

public record ReportModerationResult(
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
