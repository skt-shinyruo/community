package com.nowcoder.community.content.application.result;

import java.util.Date;
import java.util.List;
import java.util.UUID;

public record PostSummaryResult(
        UUID id,
        UUID userId,
        String title,
        int type,
        int status,
        Date createTime,
        int commentCount,
        double score,
        UUID categoryId,
        List<String> tags,
        UUID lastReplyUserId,
        Date lastReplyTime,
        Date lastActivityTime,
        String lastReplyPreview
) {
}
