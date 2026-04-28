package com.nowcoder.community.search.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PostSearchDocument(
        UUID postId,
        UUID userId,
        UUID categoryId,
        List<String> tags,
        String title,
        String content,
        Integer type,
        Integer status,
        Instant createTime,
        Double score
) {

    public PostSearchDocument {
        tags = tags == null ? List.of() : List.copyOf(tags);
    }
}
