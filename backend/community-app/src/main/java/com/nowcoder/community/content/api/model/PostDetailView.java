package com.nowcoder.community.content.api.model;

import java.util.Date;
import java.util.List;

public record PostDetailView(
        int id,
        int userId,
        String title,
        String content,
        int type,
        int status,
        Date createTime,
        Date updateTime,
        int editCount,
        int commentCount,
        double score,
        Integer categoryId,
        List<String> tags,
        long likeCount,
        boolean liked,
        boolean bookmarked
) {
}
