package com.nowcoder.community.content.api.model;

import java.util.Date;
import java.util.List;
import java.util.UUID;

public record PostDetailView(
        UUID id,
        UUID userId,
        String title,
        List<PostContentBlockView> blocks,
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
    public PostDetailView {
        blocks = blocks == null ? List.of() : List.copyOf(blocks);
    }
}
