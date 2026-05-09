package com.nowcoder.community.content.application.result;

import java.util.Date;
import java.util.List;
import java.util.UUID;

public record PostDetailResult(
        UUID id,
        UUID userId,
        String title,
        List<PostContentBlockResult> blocks,
        int type,
        int status,
        Date createTime,
        Date updateTime,
        int editCount,
        int commentCount,
        double score,
        UUID categoryId,
        List<String> tags,
        long likeCount,
        boolean liked,
        boolean bookmarked
) {
    public PostDetailResult {
        blocks = blocks == null ? List.of() : List.copyOf(blocks);
    }
}
