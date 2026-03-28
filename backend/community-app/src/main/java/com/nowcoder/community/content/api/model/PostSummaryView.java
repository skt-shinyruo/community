package com.nowcoder.community.content.api.model;

import java.util.Date;
import java.util.List;

public record PostSummaryView(
        int id,
        int userId,
        String title,
        int type,
        int status,
        Date createTime,
        int commentCount,
        double score,
        Integer categoryId,
        List<String> tags,
        Integer lastReplyUserId,
        Date lastReplyTime,
        Date lastActivityTime,
        String lastReplyPreview
) {
}
